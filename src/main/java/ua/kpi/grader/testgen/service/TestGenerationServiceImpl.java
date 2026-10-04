package ua.kpi.grader.testgen.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import ua.kpi.grader.common.exception.ResourceNotFoundException;
import ua.kpi.grader.course.entity.Assignment;
import ua.kpi.grader.course.entity.Language;
import ua.kpi.grader.course.repository.AssignmentRepository;
import ua.kpi.grader.security.CurrentUser;
import ua.kpi.grader.testgen.client.ChatMessage;
import ua.kpi.grader.testgen.client.LlmClient;
import ua.kpi.grader.testgen.client.LlmResponse;
import ua.kpi.grader.testgen.client.LlmResponseParser;
import ua.kpi.grader.testgen.client.LlmUnavailableException;
import ua.kpi.grader.testgen.config.TestGenProperties;
import ua.kpi.grader.testgen.dto.IterationResponse;
import ua.kpi.grader.testgen.dto.StartTestGenerationRequest;
import ua.kpi.grader.testgen.dto.TestGenerationJobResponse;
import ua.kpi.grader.testgen.entity.JobStatus;
import ua.kpi.grader.testgen.entity.PromptType;
import ua.kpi.grader.testgen.entity.TestGenerationIteration;
import ua.kpi.grader.testgen.entity.TestGenerationJob;
import ua.kpi.grader.testgen.mutation.Mutant;
import ua.kpi.grader.testgen.mutation.MutantPool;
import ua.kpi.grader.testgen.mutation.MutantPoolBuilder;
import ua.kpi.grader.testgen.prompt.PromptBuilder;
import ua.kpi.grader.testgen.prompt.SignatureResolver;
import ua.kpi.grader.testgen.prompt.TaskSpec;
import ua.kpi.grader.testgen.repository.TestGenerationIterationRepository;
import ua.kpi.grader.testgen.repository.TestGenerationJobRepository;
import ua.kpi.grader.testgen.sandbox.SandboxResult;
import ua.kpi.grader.testgen.sandbox.SandboxRunner;
import ua.kpi.grader.testgen.sandbox.SandboxUnavailableException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Execution-feedback self-repair loop for LLM-generated unit tests.
 *
 * <pre>
 * tests = LLM(GENERATE)                                     iteration 0
 * for i in 1..maxIterations:
 *     evaluate tests on the reference solution (and mutants when valid)
 *     compile error        → REPAIR_COMPILE (compiler output)
 *     failing tests        → REPAIR_FAILING (failures)
 *     valid                → best = tests; stop unless a feedback-set mutant survives
 *                            → KILL_MUTANT (diff of the first survivor)
 *     candidate = LLM(system + self-contained repair prompt)
 *     reject candidate if it drops below minTestCount tests and has fewer tests than before
 * final: if tests valid → best = tests
 *        else if pruneFailing → drop tests failing on the reference; accept if valid and ≥ minTestCount
 * </pre>
 *
 * Held-out mutants (set B) are evaluated for every candidate but never appear in a prompt.
 * Repairs are sent without chat history (small models tend to repeat their previous answer);
 * when a repair returns an unchanged file, the next call samples at a higher temperature.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestGenerationServiceImpl implements TestGenerationService {

    /** Temperature added per consecutive repair that returned an unchanged test file. */
    private static final double STAGNATION_TEMPERATURE_STEP = 0.4;
    private static final double MAX_TEMPERATURE = 1.0;

    private static final Pattern PY_TEST = Pattern.compile("(?m)^\\s*def\\s+test_\\w*\\s*\\(");
    private static final Pattern CPP_TEST_FUNCTION =
            Pattern.compile("(?m)^\\s*(?:static\\s+)?(?:inline\\s+)?(?:void|bool|int)\\s+(test_\\w+)\\s*\\(\\s*\\)\\s*\\{");
    private static final Pattern CPP_TEST_CASE = Pattern.compile("(?m)^\\s*TEST_CASE\\s*\\(\\s*(\\w+)\\s*\\)");
    private static final Pattern CPP_PASS_LITERAL =Pattern.compile("\"PASS[ \"]");
    private static final Pattern PY_IMPORTS_PYTEST =
            Pattern.compile("(?m)^\\s*(import\\s+pytest\\b|from\\s+pytest\\s+import)");

    private final LlmClient llmClient;
    private final SandboxRunner sandboxRunner;
    private final MutantPoolBuilder mutantPoolBuilder;
    private final PromptBuilder promptBuilder;
    private final TestGenerationJobRepository jobRepository;
    private final TestGenerationIterationRepository iterationRepository;
    private final AssignmentRepository assignmentRepository;
    private final CurrentUser currentUser;
    private final TestGenProperties properties;
    private final TestGenerationWorker worker;
    private final JsonMapper jsonMapper;

    /**
     * Validates the request, stores a PENDING job and starts the loop in the background.
     *
     * @return id of the new job
     * @throws IllegalArgumentException  if neither description nor signature is given
     * @throws IllegalStateException     if the user already has a pending or running job (409)
     * @throws ResourceNotFoundException if {@code assignmentId} does not exist
     */
    @Override
    public Long startJob(StartTestGenerationRequest request) {
        if (isBlank(request.taskDescription()) && isBlank(request.functionSignature())) {
            throw new IllegalArgumentException("Task description or function signature is required");
        }
        String userId = currentUser.getUserId();
        if (jobRepository.existsByCreatedByAndStatusIn(userId, List.of(JobStatus.PENDING, JobStatus.RUNNING))) {
            throw new IllegalStateException("A test generation job is already running; wait until it finishes");
        }
        Assignment assignment = null;
        if (request.assignmentId() != null) {
            assignment = assignmentRepository.findByIdAndIsActiveTrue(request.assignmentId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Assignment not found with id: " + request.assignmentId()));
        }

        GenerationConfig config = defaultConfig().withOverrides(request.config());
        GenerationRequest generationRequest = new GenerationRequest(request.language(),
                request.taskDescription(), request.functionSignature(), request.referenceSolution());

        TestGenerationJob job = jobRepository.save(TestGenerationJob.builder()
                .assignment(assignment)
                .language(request.language())
                .model(config.model())
                .config(jsonMapper.writeValueAsString(config))
                .taskDescription(request.taskDescription())
                .functionSignature(request.functionSignature())
                .referenceSolution(request.referenceSolution())
                .createdBy(userId)
                .build());
        Long jobId = job.getId();
        worker.submit(() -> executeJob(jobId, generationRequest, config));
        log.info("Test generation job {} started by {} ({}, model {})", jobId, userId,
                request.language(), config.model());
        return jobId;
    }

    /**
     * Returns a job with its iterations.
     *
     * @throws ResourceNotFoundException if the job does not exist
     * @throws AccessDeniedException     if the job belongs to another user and the caller is not ADMIN
     */
    @Override
    @Transactional(readOnly = true)
    public TestGenerationJobResponse getJob(Long jobId) {
        TestGenerationJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Test generation job not found with id: " + jobId));
        if (!currentUser.getUserId().equals(job.getCreatedBy()) && !currentUser.hasRole("ADMIN")) {
            throw new AccessDeniedException("Test generation job " + jobId + " belongs to another user");
        }
        List<IterationResponse> iterations = iterationRepository.findAllByJobIdOrderByIterationNoAsc(jobId).stream()
                .map(IterationResponse::from)
                .toList();
        return TestGenerationJobResponse.from(job, iterations);
    }

    /**
     * Runs the loop synchronously without persistence.
     */
    @Override
    public GenerationOutcome runSync(GenerationRequest request, GenerationConfig config) {
        return runLoop(request, config, iteration -> { });
    }

    /**
     * Returns the default configuration from {@code testgen.*}.
     */
    @Override
    public GenerationConfig defaultConfig() {
        return GenerationConfig.defaults(properties);
    }

    /**
     * Marks jobs left PENDING/RUNNING by a previous process as FAILED.
     */
    @Override
    @Transactional
    public int failInterruptedJobs() {
        return jobRepository.failAllWithStatus(List.of(JobStatus.PENDING, JobStatus.RUNNING),
                "Interrupted by a server restart; please start the generation again", OffsetDateTime.now());
    }

    // ── Background job ────────────────────────────────────────

    private void executeJob(Long jobId, GenerationRequest request, GenerationConfig config) {
        TestGenerationJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.warn("Test generation job {} disappeared before it started", jobId);
            return;
        }
        job.markRunning();
        TestGenerationJob running = jobRepository.save(job);
        try {
            GenerationOutcome outcome = runLoop(request, config,
                    iteration -> iterationRepository.save(toEntity(running, iteration)));
            if (outcome.success()) {
                running.markSucceeded(outcome.finalTestContent());
            } else {
                running.markFailed(outcome.errorMessage());
            }
        } catch (LlmUnavailableException | SandboxUnavailableException e) {
            log.error("Test generation job {} failed: {}", jobId, e.getMessage());
            running.markFailed(e.getMessage());
        } catch (RuntimeException e) {
            // Background job: any failure must still leave the job in a terminal state.
            log.error("Test generation job {} crashed", jobId, e);
            running.markFailed("Unexpected error: " + e.getMessage());
        }
        jobRepository.save(running);
        log.info("Test generation job {} finished with {}", jobId, running.getStatus());
    }

    private static TestGenerationIteration toEntity(TestGenerationJob job, IterationRecord r) {
        return TestGenerationIteration.builder()
                .job(job)
                .iterationNo(r.iterationNo())
                .promptType(r.promptType())
                .prompt(r.prompt())
                .llmOutput(r.llmOutput())
                .testContent(r.testContent())
                .feedback(r.feedback())
                .compileOk(r.compileOk())
                .refPassed(r.refPassed())
                .refTotal(r.refTotal())
                .mutantsKilled(r.mutantsKilled())
                .mutantsTotal(r.mutantsTotal())
                .heldoutKilled(r.heldoutKilled())
                .heldoutTotal(r.heldoutTotal())
                .coveragePct(r.coveragePct())
                .testCount(r.testCount())
                .accepted(r.accepted())
                .temperature(r.temperature())
                .durationMs(r.durationMs())
                .promptTokens(r.promptTokens())
                .completionTokens(r.completionTokens())
                .build();
    }

    // ── Self-repair loop ──────────────────────────────────────

    GenerationOutcome runLoop(GenerationRequest request, GenerationConfig config, Consumer<IterationRecord> listener) {
        long start = System.nanoTime();
        Language language = request.language();
        MutantPool pool = mutantPoolBuilder.build(request.referenceSolution(), language,
                config.poolSeed(), config.maxMutants());

        String signature = SignatureResolver.resolve(language, request.functionSignature(),
                request.referenceSolution());
        if (signature != null && !signature.strip().equals(
                request.functionSignature() == null ? "" : request.functionSignature().strip())) {
            log.info("Using signature from reference solution: '{}' (declared '{}')",
                    signature, request.functionSignature());
        }
        TaskSpec task = new TaskSpec(language, request.taskDescription(), signature);
        String systemPrompt = promptBuilder.system();
        String generatePrompt = promptBuilder.generate(task, config.minTestCount());
        List<IterationRecord> iterations = new ArrayList<>();

        // Iteration 0: initial generation.
        long iterationStart = System.nanoTime();
        LlmResponse response = llmClient.chat(config.model(), messages(systemPrompt, generatePrompt),
                config.temperature(), config.seed());
        String tests = normalize(language, LlmResponseParser.extractCode(response.content()));
        Evaluation current = evaluate(request, tests, pool);
        record(iterations, listener, 0, PromptType.GENERATE, generatePrompt, response, tests, null,
                current, countTests(language, tests), true, config.temperature(), iterationStart);

        String best = null;
        int stagnation = 0;
        for (int i = 1; i <= config.maxIterations(); i++) {
            PromptType type;
            String feedback;
            String prompt;
            if (!current.ref().compiled()) {
                type = PromptType.REPAIR_COMPILE;
                feedback = compileErrors(current.ref());
                prompt = promptBuilder.repairCompile(task, tests, feedback);
            } else if (!current.valid()) {
                type = PromptType.REPAIR_FAILING;
                String problem = runProblem(current.ref());
                feedback = describeFailures(current.ref(), problem);
                prompt = promptBuilder.repairFailing(task, tests, current.ref().failures(), problem);
            } else {
                best = tests;
                if (!config.mutationFeedback() || current.survivors().isEmpty()) {
                    break;
                }
                type = PromptType.KILL_MUTANT;
                feedback = current.survivors().getFirst().diff();
                prompt = promptBuilder.killMutant(task, tests, feedback);
            }
            if (pool.leaksHeldOut(prompt)) {
                throw new IllegalStateException("Held-out mutant leaked into a prompt");
            }

            double temperature = Math.round(100 * Math.min(MAX_TEMPERATURE,
                    config.temperature() + stagnation * STAGNATION_TEMPERATURE_STEP)) / 100.0;
            iterationStart = System.nanoTime();
            response = llmClient.chat(config.model(), messages(systemPrompt, prompt), temperature, config.seed());
            String candidate = normalize(language, LlmResponseParser.extractCode(response.content()));
            Evaluation candidateEval = evaluate(request, candidate, pool);
            int candidateCount = countTests(language, candidate);
            int currentCount = countTests(language, tests);
            boolean accepted = candidateCount >= config.minTestCount() || candidateCount >= currentCount;
            stagnation = candidate.strip().equals(tests.strip()) ? stagnation + 1 : 0;

            record(iterations, listener, i, type, prompt, response, candidate, feedback,
                    candidateEval, candidateCount, accepted, temperature, iterationStart);
            if (accepted) {
                tests = candidate;
                current = candidateEval;
            } else {
                log.info("Rejected candidate in iteration {}: {} tests (min {}, previous {})",
                        i, candidateCount, config.minTestCount(), currentCount);
            }
        }
        if (current.valid()) {
            best = tests;
        } else if (config.pruneFailing() && best == null) {
            String pruned = prune(request, tests, current, pool, config, iterations, listener);
            if (pruned != null) {
                tests = pruned;
                best = pruned;
            }
        }

        long totalMs = (System.nanoTime() - start) / 1_000_000;
        String error = best != null ? null : failureReason(current, config.maxIterations());
        return new GenerationOutcome(best != null, best, tests, error, List.copyOf(iterations), pool, totalMs);
    }

    /**
     * Removes the tests that fail on the reference solution and re-evaluates the file.
     * Recorded as a PRUNE_FAILING iteration (no LLM call).
     *
     * @return the pruned file if it is valid and keeps at least {@code minTestCount} tests, else null
     */
    private String prune(GenerationRequest request, String tests, Evaluation current, MutantPool pool,
                         GenerationConfig config, List<IterationRecord> iterations,
                         Consumer<IterationRecord> listener) {
        SandboxResult ref = current.ref();
        if (!ref.compiled() || ref.timedOut() || ref.failures().isEmpty() || ref.passed() < config.minTestCount()) {
            return null;
        }
        Set<String> failing = new LinkedHashSet<>();
        ref.failures().forEach(f -> failing.add(f.name()));
        String pruned = TestPruner.remove(request.language(), tests, failing);
        if (pruned == null) {
            log.info("Could not prune failing tests {}", failing);
            return null;
        }
        long start = System.nanoTime();
        Evaluation eval = evaluate(request, pruned, pool);
        int count = countTests(request.language(), pruned);
        boolean accepted = eval.valid() && count >= config.minTestCount();
        IterationRecord record = new IterationRecord(iterations.size(), PromptType.PRUNE_FAILING, null, null,
                pruned, "Removed failing tests: " + String.join(", ", failing),
                eval.ref().compiled(), eval.ref().passed(), eval.ref().total(), eval.valid(),
                eval.killedA(), eval.totalA(), eval.killedB(), eval.totalB(), eval.ref().coveragePct(),
                count, accepted, null, (System.nanoTime() - start) / 1_000_000, null, null);
        iterations.add(record);
        listener.accept(record);
        log.info("Pruned {} failing test(s): valid={} tests={}", failing.size(), eval.valid(), count);
        return accepted ? pruned : null;
    }

    private static List<ChatMessage> messages(String systemPrompt, String userPrompt) {
        return List.of(ChatMessage.system(systemPrompt), ChatMessage.user(userPrompt));
    }

    /**
     * Deterministic clean-up applied to every extracted test file: adds {@code import pytest}
     * when a Python file uses {@code pytest.} without importing it (a frequent small-model slip).
     */
    static String normalize(Language language, String tests) {
        if (language == Language.PYTHON && tests.contains("pytest.") && !PY_IMPORTS_PYTEST.matcher(tests).find()) {
            return "import pytest\n" + tests;
        }
        return tests;
    }

    private void record(List<IterationRecord> iterations, Consumer<IterationRecord> listener, int iterationNo,
                        PromptType type, String prompt, LlmResponse response, String tests, String feedback,
                        Evaluation eval, int testCount, boolean accepted, double temperature, long startNanos) {
        IterationRecord record = new IterationRecord(iterationNo, type, prompt, response.content(), tests, feedback,
                eval.ref().compiled(), eval.ref().passed(), eval.ref().total(), eval.valid(),
                eval.killedA(), eval.totalA(), eval.killedB(), eval.totalB(), eval.ref().coveragePct(),
                testCount, accepted, temperature, (System.nanoTime() - startNanos) / 1_000_000,
                response.promptTokens(), response.completionTokens());
        iterations.add(record);
        listener.accept(record);
        log.info("Iteration {} {}: compiled={} ref={}/{} mutantsA={}/{} heldoutB={}/{} tests={} accepted={}",
                iterationNo, type, record.compileOk(), record.refPassed(), record.refTotal(),
                record.mutantsKilled(), record.mutantsTotal(), record.heldoutKilled(), record.heldoutTotal(),
                testCount, accepted);
    }

    // ── Evaluation of one test file ───────────────────────────

    private Evaluation evaluate(GenerationRequest request, String tests, MutantPool pool) {
        SandboxResult ref = sandboxRunner.runWithCoverage(request.language(), request.referenceSolution(), tests);
        int totalA = pool.feedback().size();
        int totalB = pool.heldOut().size();
        if (!ref.allPassed() || totalA + totalB == 0) {
            return new Evaluation(ref, ref.allPassed(), null, totalA, null, totalB, List.of());
        }
        List<Mutant> all = Stream.concat(pool.feedback().stream(), pool.heldOut().stream()).toList();
        List<SandboxResult> results = sandboxRunner.runAgainstMutants(request.language(), tests,
                all.stream().map(Mutant::source).toList());

        List<Mutant> survivors = new ArrayList<>();
        int killedA = 0;
        int killedB = 0;
        for (int i = 0; i < all.size(); i++) {
            boolean killed = !results.get(i).allPassed();
            if (i < totalA) {
                if (killed) {
                    killedA++;
                } else {
                    survivors.add(all.get(i));
                }
            } else if (killed) {
                killedB++;
            }
        }
        return new Evaluation(ref, true, killedA, totalA, killedB, totalB, List.copyOf(survivors));
    }

    /**
     * @param survivors feedback-set mutants not detected by the tests, in pool order
     */
    private record Evaluation(SandboxResult ref, boolean valid, Integer killedA, int totalA,
                              Integer killedB, int totalB, List<Mutant> survivors) {}

    // ── Helpers ───────────────────────────────────────────────

    /**
     * Counts test cases statically: pytest functions for Python; distinct {@code TEST_CASE(name)}
     * harness tests and {@code test_*} functions (or {@code "PASS"} print sites as a fallback) for C/C++.
     */
    static int countTests(Language language, String tests) {
        if (tests == null || tests.isBlank()) {
            return 0;
        }
        if (language == Language.PYTHON) {
            return (int) PY_TEST.matcher(tests).results().count();
        }
        Set<String> names = new LinkedHashSet<>();
        Matcher m = CPP_TEST_FUNCTION.matcher(tests);
        while (m.find()) {
            names.add(m.group(1));
        }
        Matcher harness = CPP_TEST_CASE.matcher(tests);
        while (harness.find()) {
            names.add(harness.group(1));
        }
        return names.isEmpty() ? (int) CPP_PASS_LITERAL.matcher(tests).results().count() : names.size();
    }

    private static String compileErrors(SandboxResult ref) {
        if (ref.timedOut() && ref.compileOutput().isBlank()) {
            return "Compilation did not finish within the time limit.";
        }
        return ref.compileOutput().isBlank() ? ref.rawOutput() : ref.compileOutput();
    }

    /** Explains a non-valid run that has no individual failures, or null. */
    private static String runProblem(SandboxResult ref) {
        if (ref.timedOut()) {
            return "The tests did not finish within the time limit (infinite loop or very slow test).";
        }
        if (ref.total() == 0) {
            return "No tests were found or run. Follow the test file conventions exactly.";
        }
        return null;
    }

    private static String describeFailures(SandboxResult ref, String problem) {
        StringBuilder sb = new StringBuilder();
        ref.failures().forEach(f -> sb.append(f.name()).append(": ").append(f.message()).append('\n'));
        if (problem != null) {
            sb.append(problem);
        }
        return sb.toString().strip();
    }

    private static String failureReason(Evaluation last, int maxIterations) {
        String state = !last.ref().compiled() ? "the tests do not compile"
                : last.ref().timedOut() ? "the tests time out"
                : last.ref().total() == 0 ? "no tests were found"
                : last.ref().failed() + " of " + last.ref().total() + " tests fail on the reference solution";
        return "No valid test file after " + maxIterations + " repair iteration(s): " + state + ".";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
