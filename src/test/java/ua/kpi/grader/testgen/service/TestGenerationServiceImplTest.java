package ua.kpi.grader.testgen.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;
import ua.kpi.grader.common.exception.ResourceNotFoundException;
import ua.kpi.grader.course.entity.Language;
import ua.kpi.grader.course.repository.AssignmentRepository;
import ua.kpi.grader.security.CurrentUser;
import ua.kpi.grader.testgen.client.ChatMessage;
import ua.kpi.grader.testgen.client.LlmResponse;
import ua.kpi.grader.testgen.client.LlmUnavailableException;
import ua.kpi.grader.testgen.client.OllamaClient;
import ua.kpi.grader.testgen.config.TestGenProperties;
import ua.kpi.grader.testgen.dto.GenerationOverrides;
import ua.kpi.grader.testgen.dto.StartTestGenerationRequest;
import ua.kpi.grader.testgen.entity.JobStatus;
import ua.kpi.grader.testgen.entity.PromptType;
import ua.kpi.grader.testgen.entity.TestGenerationIteration;
import ua.kpi.grader.testgen.entity.TestGenerationJob;
import ua.kpi.grader.testgen.mutation.Mutant;
import ua.kpi.grader.testgen.mutation.MutantPool;
import ua.kpi.grader.testgen.mutation.MutantPoolBuilder;
import ua.kpi.grader.testgen.mutation.MutationOperator;
import ua.kpi.grader.testgen.prompt.PromptBuilder;
import ua.kpi.grader.testgen.repository.TestGenerationIterationRepository;
import ua.kpi.grader.testgen.repository.TestGenerationJobRepository;
import ua.kpi.grader.testgen.sandbox.SandboxResult;
import ua.kpi.grader.testgen.sandbox.SandboxRunner;
import ua.kpi.grader.testgen.sandbox.TestFailure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestGenerationServiceImplTest {

    private static final String REFERENCE = "def add(a, b):\n    return a + b\n";
    private static final Mutant MUTANT_A = new Mutant("M1", MutationOperator.ARITHMETIC, 2,
            "def add(a, b):\n    return a - b\n", "-    return a + b\n+    return a - b\n");
    private static final Mutant MUTANT_B = new Mutant("M2", MutationOperator.RETURN_VALUE, 2,
            "def add(a, b):\n    return 0\n", "-    return a + b\n+    return 0\n");

    @Mock private OllamaClient ollamaClient;
    @Mock private SandboxRunner sandboxRunner;
    @Mock private MutantPoolBuilder mutantPoolBuilder;
    @Mock private TestGenerationJobRepository jobRepository;
    @Mock private TestGenerationIterationRepository iterationRepository;
    @Mock private AssignmentRepository assignmentRepository;
    @Mock private CurrentUser currentUser;
    @Mock private TestGenerationWorker worker;

    private TestGenerationServiceImpl service;

    /** Sandbox result for the reference solution, by test file content. */
    private final Map<String, SandboxResult> referenceResults = new HashMap<>();
    /** Mutant run results (true = killed), by test file content; order = feedback set, then held-out set. */
    private final Map<String, List<Boolean>> mutantKills = new HashMap<>();

    @BeforeEach
    void setUp() {
        TestGenProperties properties = new TestGenProperties(
                new TestGenProperties.Ollama("http://localhost:11434", "qwen2.5-coder:3b", 180, "30m", 8192),
                0.2, 3, 5, true, 10, true,
                new TestGenProperties.Sandbox("cpp:1", "py:1", 20, 5, "256m", "1"));
        service = new TestGenerationServiceImpl(ollamaClient, sandboxRunner, mutantPoolBuilder, new PromptBuilder(),
                jobRepository, iterationRepository, assignmentRepository, currentUser, properties, worker,
                JsonMapper.builder().build());

        when(mutantPoolBuilder.build(anyString(), any(), anyLong(), anyInt())).thenReturn(MutantPool.empty());
        when(sandboxRunner.runWithCoverage(eq(Language.PYTHON), eq(REFERENCE), anyString()))
                .thenAnswer(inv -> referenceResults.getOrDefault(inv.getArgument(2, String.class), passing(1)));
        when(sandboxRunner.runAgainstMutants(eq(Language.PYTHON), anyString(), anyList()))
                .thenAnswer(inv -> mutantKills.getOrDefault(inv.getArgument(1, String.class), List.of()).stream()
                        .map(killed -> killed ? failing(1, 1) : passing(1))
                        .toList());
    }

    // ── Loop scenarios ────────────────────────────────────────

    @Test
    void validOnFirstTry_succeedsAfterOneIteration() {
        String tests = testFile("v1", 5);
        referenceResults.put(tests, passing(5));
        llmAnswers(tests);

        GenerationOutcome outcome = service.runSync(request(), config(3, true));

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.finalTestContent()).isEqualTo(tests);
        assertThat(outcome.iterations()).hasSize(1);
        assertThat(outcome.iterations().getFirst().promptType()).isEqualTo(PromptType.GENERATE);
        assertThat(outcome.iterations().getFirst().coveragePct()).isEqualTo(100.0);
        verify(ollamaClient, times(1)).chat(anyString(), anyList(), anyDouble(), any());
    }

    @Test
    void generatePrompt_doesNotContainReferenceSolution() {
        String tests = testFile("v1", 5);
        referenceResults.put(tests, passing(5));
        llmAnswers(tests);

        GenerationOutcome outcome = service.runSync(request(), config(3, true));

        assertThat(outcome.iterations().getFirst().prompt()).doesNotContain("return a + b");
    }

    @Test
    void compileErrorThenFixed_succeedsAfterRepair() {
        String broken = testFile("broken", 5);
        String fixed = testFile("fixed", 5);
        referenceResults.put(broken, compileError("SyntaxError: invalid syntax (line 3)"));
        referenceResults.put(fixed, passing(5));
        llmAnswers(broken, fixed);

        GenerationOutcome outcome = service.runSync(request(), config(3, true));

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.finalTestContent()).isEqualTo(fixed);
        assertThat(outcome.iterations()).extracting(IterationRecord::promptType)
                .containsExactly(PromptType.GENERATE, PromptType.REPAIR_COMPILE);
        IterationRecord repair = outcome.iterations().get(1);
        assertThat(repair.prompt()).contains("SyntaxError: invalid syntax (line 3)", "# broken");
        assertThat(repair.feedback()).contains("SyntaxError");
        assertThat(outcome.iterations().getFirst().compileOk()).isFalse();
        assertThat(repair.compileOk()).isTrue();
    }

    @Test
    void failingTestThenFixed_succeedsAfterRepair() {
        String wrong = testFile("wrong", 5);
        String fixed = testFile("fixed", 5);
        referenceResults.put(wrong, failing(4, 1));
        referenceResults.put(fixed, passing(5));
        llmAnswers(wrong, fixed);

        GenerationOutcome outcome = service.runSync(request(), config(3, true));

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.iterations()).extracting(IterationRecord::promptType)
                .containsExactly(PromptType.GENERATE, PromptType.REPAIR_FAILING);
        assertThat(outcome.iterations().get(1).prompt()).contains("test_wrong_0: assert 5 == 6");
        assertThat(outcome.iterations().getFirst().refPassed()).isEqualTo(4);
        assertThat(outcome.iterations().getFirst().refTotal()).isEqualTo(5);
    }

    @Test
    void survivingMutant_isFedBackAndKilled_heldOutNeverShown() {
        when(mutantPoolBuilder.build(anyString(), any(), anyLong(), anyInt()))
                .thenReturn(new MutantPool(List.of(MUTANT_A), List.of(MUTANT_B)));
        String weak = testFile("weak", 5);
        String strong = testFile("strong", 6);
        referenceResults.put(weak, passing(5));
        referenceResults.put(strong, passing(6));
        mutantKills.put(weak, List.of(false, true));
        mutantKills.put(strong, List.of(true, true));
        llmAnswers(weak, strong);

        GenerationOutcome outcome = service.runSync(request(), config(3, true));

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.finalTestContent()).isEqualTo(strong);
        assertThat(outcome.iterations()).extracting(IterationRecord::promptType)
                .containsExactly(PromptType.GENERATE, PromptType.KILL_MUTANT);
        IterationRecord first = outcome.iterations().getFirst();
        assertThat(first.mutantsKilled()).isZero();
        assertThat(first.mutantsTotal()).isEqualTo(1);
        assertThat(first.heldoutKilled()).isEqualTo(1);
        IterationRecord kill = outcome.iterations().get(1);
        assertThat(kill.prompt()).contains(MUTANT_A.diff()).doesNotContain(MUTANT_B.diff());
        assertThat(kill.mutantsKilled()).isEqualTo(1);
        assertThat(kill.heldoutKilled()).isEqualTo(1);
    }

    @Test
    void mutationFeedbackDisabled_stopsAtFirstValidSuite() {
        when(mutantPoolBuilder.build(anyString(), any(), anyLong(), anyInt()))
                .thenReturn(new MutantPool(List.of(MUTANT_A), List.of(MUTANT_B)));
        String weak = testFile("weak", 5);
        referenceResults.put(weak, passing(5));
        mutantKills.put(weak, List.of(false, false));
        llmAnswers(weak);

        GenerationOutcome outcome = service.runSync(request(), config(3, false));

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.iterations()).hasSize(1);
        assertThat(outcome.iterations().getFirst().heldoutKilled()).isZero();
    }

    @Test
    void maxIterationsExhausted_withoutValidVersion_fails() {
        String a = testFile("a", 5);
        String b = testFile("b", 5);
        String c = testFile("c", 5);
        String d = testFile("d", 5);
        for (String t : List.of(a, b, c, d)) {
            referenceResults.put(t, failing(3, 2));
        }
        llmAnswers(a, b, c, d);

        GenerationOutcome outcome = service.runSync(request(), config(3, true));

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.finalTestContent()).isNull();
        assertThat(outcome.lastTestContent()).isEqualTo(d);
        assertThat(outcome.iterations()).hasSize(4);
        assertThat(outcome.errorMessage()).contains("3 repair iteration", "2 of 5 tests fail");
    }

    @Test
    void antiCheat_rejectsRepairThatDeletesTests() {
        String original = testFile("original", 6);
        String gutted = testFile("gutted", 2);
        String fixed = testFile("fixed", 6);
        referenceResults.put(original, failing(5, 1));
        referenceResults.put(gutted, passing(2));
        referenceResults.put(fixed, passing(6));
        llmAnswers(original, gutted, fixed);

        GenerationOutcome outcome = service.runSync(request(), config(3, true));

        assertThat(outcome.iterations()).hasSize(3);
        IterationRecord rejected = outcome.iterations().get(1);
        assertThat(rejected.accepted()).isFalse();
        assertThat(rejected.testCount()).isEqualTo(2);
        // The next repair still works on the original file, not the rejected one.
        assertThat(outcome.iterations().get(2).prompt()).contains("# original").doesNotContain("# gutted");
        assertThat(outcome.finalTestContent()).isEqualTo(fixed);
    }

    @Test
    void singleShot_returnsGeneratedTestsWithoutRepair() {
        String wrong = testFile("wrong", 5);
        referenceResults.put(wrong, failing(4, 1));
        llmAnswers(wrong);

        GenerationOutcome outcome = service.runSync(request(), config(0, true));

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.lastTestContent()).isEqualTo(wrong);
        assertThat(outcome.iterations()).hasSize(1);
        verify(ollamaClient, times(1)).chat(anyString(), anyList(), anyDouble(), any());
    }

    @Test
    void singleShot_validTestsSucceed() {
        String tests = testFile("ok", 5);
        referenceResults.put(tests, passing(5));
        llmAnswers(tests);

        GenerationOutcome outcome = service.runSync(request(), config(0, true));

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.finalTestContent()).isEqualTo(tests);
    }

    @Test
    void repairs_areSentWithoutHistoryButWithTaskSummary() {
        String a = testFile("a", 5);
        String b = testFile("b", 5);
        referenceResults.put(a, failing(4, 1));
        referenceResults.put(b, passing(5));
        llmAnswers(a, b);

        service.runSync(request(), config(3, true));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(ollamaClient, times(2)).chat(anyString(), captor.capture(), anyDouble(), any());
        List<ChatMessage> repair = captor.getAllValues().get(1);
        assertThat(repair).extracting(ChatMessage::role).containsExactly("system", "user");
        assertThat(repair.get(1).content()).contains("## Task", "Add two numbers.", "# a");
    }

    @Test
    void unchangedRepair_raisesTemperatureForNextCall() {
        String same = testFile("same", 5);
        String fixed = testFile("fixed", 5);
        referenceResults.put(same, failing(4, 1));
        referenceResults.put(fixed, passing(5));
        llmAnswers(same, same, same, fixed);

        GenerationOutcome outcome = service.runSync(request(), config(3, true));

        assertThat(outcome.iterations()).extracting(IterationRecord::temperature)
                .containsExactly(0.2, 0.2, 0.6, 1.0);
        assertThat(outcome.success()).isTrue();
    }

    @Test
    void pruning_removesTestsStillFailingAfterLoop() {
        String stubborn = testFile("wrong", 6);
        String pruned = TestPruner.remove(Language.PYTHON, stubborn, java.util.Set.of("test_wrong_0"));
        referenceResults.put(stubborn, failing(5, 1));
        referenceResults.put(pruned, passing(5));
        llmAnswers(stubborn, stubborn, stubborn, stubborn);

        GenerationOutcome outcome = service.runSync(request(), withPruning(config(3, true)));

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.finalTestContent()).isEqualTo(pruned).doesNotContain("def test_wrong_0(");
        IterationRecord last = outcome.iterations().getLast();
        assertThat(last.promptType()).isEqualTo(PromptType.PRUNE_FAILING);
        assertThat(last.iterationNo()).isEqualTo(4);
        assertThat(last.accepted()).isTrue();
        assertThat(last.temperature()).isNull();
        assertThat(last.feedback()).contains("test_wrong_0");
        verify(ollamaClient, times(4)).chat(anyString(), anyList(), anyDouble(), any());
    }

    @Test
    void pruning_isSkipped_whenTooFewTestsWouldRemain() {
        String stubborn = testFile("wrong", 5);
        referenceResults.put(stubborn, failing(4, 1));
        llmAnswers(stubborn, stubborn, stubborn, stubborn);

        GenerationOutcome outcome = service.runSync(request(), withPruning(config(3, true)));

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.iterations()).extracting(IterationRecord::promptType)
                .doesNotContain(PromptType.PRUNE_FAILING);
    }

    private static GenerationConfig withPruning(GenerationConfig c) {
        return new GenerationConfig(c.model(), c.maxIterations(), c.mutationFeedback(), c.temperature(),
                c.seed(), c.minTestCount(), c.maxMutants(), true);
    }

    @Test
    void normalize_addsMissingPytestImport() {
        String usesRaises = "from solution import *\n\ndef test_neg():\n    with pytest.raises(ValueError):\n        f(-1)\n";
        String imported = "import pytest\n" + usesRaises;

        assertThat(TestGenerationServiceImpl.normalize(Language.PYTHON, usesRaises)).isEqualTo(imported);
        assertThat(TestGenerationServiceImpl.normalize(Language.PYTHON, imported)).isEqualTo(imported);
        assertThat(TestGenerationServiceImpl.normalize(Language.CPP, "pytest.x")).isEqualTo("pytest.x");
    }

    // ── Jobs ──────────────────────────────────────────────────

    @Test
    void startJob_persistsJobAndRunsLoopInBackground() {
        when(currentUser.getUserId()).thenReturn("teacher-1");
        List<TestGenerationJob> saved = stubJobPersistence();
        String tests = testFile("ok", 5);
        referenceResults.put(tests, passing(5));
        llmAnswers(tests);

        Long jobId = service.startJob(startRequest(null));

        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(worker).submit(task.capture());
        assertThat(saved.getFirst().getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(saved.getFirst().getConfig()).contains("\"maxIterations\":2");

        task.getValue().run();

        TestGenerationJob finished = saved.getLast();
        assertThat(finished.getId()).isEqualTo(jobId);
        assertThat(finished.getStatus()).isEqualTo(JobStatus.SUCCEEDED);
        assertThat(finished.getFinalTestContent()).isEqualTo(tests);
        assertThat(finished.getFinishedAt()).isNotNull();
        verify(iterationRepository).save(any(TestGenerationIteration.class));
    }

    @Test
    void startJob_marksJobFailed_whenLlmUnavailable() {
        when(currentUser.getUserId()).thenReturn("teacher-1");
        List<TestGenerationJob> saved = stubJobPersistence();
        when(ollamaClient.chat(anyString(), anyList(), anyDouble(), any()))
                .thenThrow(new LlmUnavailableException("LLM server not reachable", null));

        service.startJob(startRequest(null));
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(worker).submit(task.capture());
        task.getValue().run();

        assertThat(saved.getLast().getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(saved.getLast().getErrorMessage()).contains("not reachable");
    }

    @Test
    void startJob_rejectsSecondRunningJob() {
        when(currentUser.getUserId()).thenReturn("teacher-1");
        when(jobRepository.existsByCreatedByAndStatusIn(eq("teacher-1"), anyList())).thenReturn(true);

        assertThatThrownBy(() -> service.startJob(startRequest(null)))
                .isInstanceOf(IllegalStateException.class);
        verify(worker, never()).submit(any());
    }

    @Test
    void startJob_requiresDescriptionOrSignature() {
        StartTestGenerationRequest request = new StartTestGenerationRequest(
                null, " ", null, Language.PYTHON, REFERENCE, null);

        assertThatThrownBy(() -> service.startJob(request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void startJob_throwsNotFound_forUnknownAssignment() {
        when(currentUser.getUserId()).thenReturn("teacher-1");
        when(assignmentRepository.findByIdAndIsActiveTrue(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.startJob(startRequest(99L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void getJob_throwsNotFound_forUnknownJob() {
        when(jobRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getJob(42L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("42");
    }

    @Test
    void getJob_deniesOtherTeachersJob() {
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job(1L, "teacher-2")));
        when(currentUser.getUserId()).thenReturn("teacher-1");
        when(currentUser.hasRole("ADMIN")).thenReturn(false);

        assertThatThrownBy(() -> service.getJob(1L)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getJob_allowsAdminAndReturnsIterations() {
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job(1L, "teacher-2")));
        when(currentUser.getUserId()).thenReturn("admin-1");
        when(currentUser.hasRole("ADMIN")).thenReturn(true);
        when(iterationRepository.findAllByJobIdOrderByIterationNoAsc(1L)).thenReturn(List.of(
                TestGenerationIteration.builder().iterationNo(0).promptType(PromptType.GENERATE)
                        .compileOk(true).refPassed(5).refTotal(5).testCount(5).accepted(true).build()));

        var response = service.getJob(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.iterations()).singleElement()
                .satisfies(it -> assertThat(it.refPassed()).isEqualTo(5));
    }

    @Test
    void countTests_countsCppTestFunctionsAndFallsBackToPassLiterals() {
        String functions = "void test_a() {\n}\nstatic void test_b() {\n}\nint main() { test_a(); test_b(); }\n";
        String inline = "int main() {\n  std::cout << \"PASS one\";\n  std::cout << \"PASS two\";\n}\n";

        assertThat(TestGenerationServiceImpl.countTests(Language.CPP, functions)).isEqualTo(2);
        assertThat(TestGenerationServiceImpl.countTests(Language.CPP, inline)).isEqualTo(2);
        assertThat(TestGenerationServiceImpl.countTests(Language.PYTHON, testFile("x", 3))).isEqualTo(3);
        assertThat(TestGenerationServiceImpl.countTests(Language.PYTHON, "")).isZero();
    }

    // ── Helpers ───────────────────────────────────────────────

    private static GenerationRequest request() {
        return new GenerationRequest(Language.PYTHON, "Add two numbers.", "def add(a, b):", REFERENCE);
    }

    private static GenerationConfig config(int maxIterations, boolean mutationFeedback) {
        return new GenerationConfig("qwen2.5-coder:3b", maxIterations, mutationFeedback, 0.2, 1, 5, 10, false);
    }

    private static StartTestGenerationRequest startRequest(Long assignmentId) {
        return new StartTestGenerationRequest(assignmentId, "Add two numbers.", "def add(a, b):",
                Language.PYTHON, REFERENCE, new GenerationOverrides(2, null, null, 7, null, null));
    }

    /** Python test file with a marker comment and {@code count} test functions; ends with a newline. */
    private static String testFile(String marker, int count) {
        StringBuilder sb = new StringBuilder("from solution import *\n# ").append(marker).append('\n');
        for (int i = 0; i < count; i++) {
            sb.append("\ndef test_").append(marker).append('_').append(i).append("():\n    assert True\n");
        }
        return sb.toString();
    }

    private void llmAnswers(String... testFiles) {
        List<LlmResponse> responses = new ArrayList<>();
        for (String t : testFiles) {
            responses.add(new LlmResponse("```python\n" + t + "```", "qwen2.5-coder:3b", 100, 200, 10));
        }
        var stub = when(ollamaClient.chat(anyString(), anyList(), anyDouble(), any()));
        stub.thenReturn(responses.getFirst(), responses.subList(1, responses.size()).toArray(LlmResponse[]::new));
    }

    private static SandboxResult passing(int n) {
        return new SandboxResult(true, "", n, 0, n, List.of(), false, "", 100.0);
    }

    private static SandboxResult failing(int passed, int failed) {
        List<TestFailure> failures = new ArrayList<>();
        for (int i = 0; i < failed; i++) {
            failures.add(new TestFailure("test_wrong_" + i, "assert 5 == 6"));
        }
        return new SandboxResult(true, "", passed, failed, passed + failed, failures, false, "", 80.0);
    }

    private static SandboxResult compileError(String message) {
        return new SandboxResult(false, message, 0, 0, 0, List.of(), false, message, null);
    }

    private static TestGenerationJob job(Long id, String createdBy) {
        TestGenerationJob job = TestGenerationJob.builder()
                .language(Language.PYTHON).model("m").config("{}")
                .referenceSolution(REFERENCE).createdBy(createdBy).build();
        ReflectionTestUtils.setField(job, "id", id);
        return job;
    }

    /** Captures every saved job state; assigns id 1 and serves it from findById. */
    private List<TestGenerationJob> stubJobPersistence() {
        List<TestGenerationJob> saved = new ArrayList<>();
        when(jobRepository.save(any(TestGenerationJob.class))).thenAnswer(inv -> {
            TestGenerationJob job = inv.getArgument(0);
            if (job.getId() == null) {
                ReflectionTestUtils.setField(job, "id", 1L);
            }
            saved.add(snapshot(job));
            return job;
        });
        when(jobRepository.findById(1L)).thenAnswer(inv -> Optional.of(saved.getFirst()));
        return saved;
    }

    private static TestGenerationJob snapshot(TestGenerationJob job) {
        TestGenerationJob copy = TestGenerationJob.builder()
                .status(job.getStatus()).language(job.getLanguage()).model(job.getModel())
                .config(job.getConfig()).referenceSolution(job.getReferenceSolution())
                .finalTestContent(job.getFinalTestContent()).errorMessage(job.getErrorMessage())
                .createdBy(job.getCreatedBy()).finishedAt(job.getFinishedAt()).build();
        ReflectionTestUtils.setField(copy, "id", job.getId());
        return copy;
    }
}
