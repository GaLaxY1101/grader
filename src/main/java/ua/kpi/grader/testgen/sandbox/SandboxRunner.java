package ua.kpi.grader.testgen.sandbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ua.kpi.grader.course.entity.Language;
import ua.kpi.grader.testgen.config.TestGenProperties;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs a test file against a solution inside an isolated, network-less Docker container
 * and parses the outcome.
 *
 * <p>Both files are streamed into the container over stdin as a tar archive and unpacked into
 * a private tmpfs at {@code /work}; no host directory is shared. This works the same whether the
 * backend runs directly on the host or inside a container that uses the host Docker daemon
 * through {@code /var/run/docker.sock}. A single container run first compiles (C/C++) or
 * syntax-checks (Python) the sources, then runs the tests. Marker lines printed by the container script separate the compile phase from the
 * test phase so compile errors can be told apart from failing tests.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SandboxRunner {

    static final int MAX_OUTPUT_CHARS = 4000;
    private static final int MAX_FAILURE_MESSAGE_CHARS = 500;
    /** Hard cap on bytes read from the container, so a test printing in a loop cannot exhaust memory. */
    private static final int MAX_CAPTURE_BYTES = 256 * 1024;

    static final String MARKER = "@@SANDBOX:";
    private static final Pattern COMPILE_EXIT = Pattern.compile("^" + MARKER + "COMPILE_EXIT=(\\d+)$", Pattern.MULTILINE);
    private static final Pattern RUN_EXIT = Pattern.compile("^" + MARKER + "RUN_EXIT=(\\d+)$", Pattern.MULTILINE);

    private static final Pattern PYTEST_RESULT =
            Pattern.compile("^(PASSED|FAILED|ERROR) (\\S+?)(?: - (.*))?$", Pattern.MULTILINE);
    private static final Pattern PYTEST_COVERAGE =
            Pattern.compile("^solution\\.py\\s+\\d+\\s+\\d+\\s+(\\d+(?:\\.\\d+)?)%", Pattern.MULTILINE);

    private static final Pattern CPP_PASS = Pattern.compile("^PASS\\s+([^:\\s]+)", Pattern.MULTILINE);
    private static final Pattern CPP_FAIL = Pattern.compile("^FAIL\\s+([^:\\s]+):?\\s*(.*)$", Pattern.MULTILINE);
    private static final Pattern GCOVR_LINES = Pattern.compile("^lines:\\s+(\\d+(?:\\.\\d+)?)%", Pattern.MULTILINE);

    /** pytest exit codes meaning the tests could not be collected/run at all (import error, usage error). */
    private static final List<Integer> PYTEST_NOT_RUNNABLE = List.of(2, 3, 4);

    /** Unprivileged uid/gid ("nobody") the tests run as. */
    private static final String SANDBOX_USER = "65534:65534";
    private static final String UNPACK_SOURCES = "tar -x -f - -C /work && cd /work && ";

    private final TestGenProperties properties;

    /**
     * Compiles and runs {@code testFile} against {@code solution} in the sandbox.
     *
     * @param language language of both files; decides file names, image and commands
     * @param solution solution source code
     * @param testFile test file source code
     * @return parsed outcome; {@code coveragePct} is null
     * @throws SandboxUnavailableException if Docker cannot be started
     */
    public SandboxResult run(Language language, String solution, String testFile) {
        return execute(language, solution, testFile, false);
    }

    /**
     * Same as {@link #run} but also measures line coverage of the solution
     * (pytest-cov for Python, {@code g++ --coverage} + gcovr for C/C++).
     *
     * @return parsed outcome with {@code coveragePct} set when coverage could be measured
     * @throws SandboxUnavailableException if Docker cannot be started
     */
    public SandboxResult runWithCoverage(Language language, String solution, String testFile) {
        return execute(language, solution, testFile, true);
    }

    private SandboxResult execute(Language language, String solution, String testFile, boolean coverage) {
        Path outputFile = null;
        try {
            outputFile = Files.createTempFile("grader-sandbox-", ".log");
            byte[] sources = TarArchive.of(Map.of(
                    language.getSolutionFileName(), solution,
                    language.getTestFileName(), testFile));

            String containerName = "grader-sbx-" + UUID.randomUUID();
            List<String> command = dockerCommand(containerName, language, coverage);
            boolean timedOut = runProcess(command, containerName, sources, outputFile);
            String output = readCapped(outputFile);

            return language == Language.PYTHON
                    ? parsePython(output, timedOut)
                    : parseCpp(output, timedOut);
        } catch (IOException e) {
            throw new SandboxUnavailableException("Failed to run sandbox: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SandboxUnavailableException("Sandbox run interrupted", e);
        } finally {
            deleteQuietly(outputFile);
        }
    }

    private List<String> dockerCommand(String containerName, Language language, boolean coverage) {
        TestGenProperties.Sandbox sandbox = properties.sandbox();
        String image = language == Language.PYTHON ? sandbox.pythonImage() : sandbox.cppImage();
        String script = language == Language.PYTHON ? pythonScript(coverage) : cppScript(language, coverage);
        return List.of(
                "docker", "run", "--rm", "--interactive",
                "--name", containerName,
                "--user", SANDBOX_USER,
                "--network", "none",
                "--memory", sandbox.memory(),
                "--memory-swap", sandbox.memory(),
                "--cpus", sandbox.cpus(),
                "--pids-limit", "64",
                "--read-only",
                "--tmpfs", "/tmp:rw,size=64m,mode=1777",
                "--tmpfs", "/work:rw,exec,size=64m,mode=1777",
                "--cap-drop", "ALL",
                "--security-opt", "no-new-privileges",
                "--workdir", "/work",
                image,
                "sh", "-c", UNPACK_SOURCES + script
        );
    }

    /**
     * Shell script for C/C++: compile, print marker + compiler output, then run the binary.
     * Everything is piped through {@code head} so endless output is cut off inside the container.
     */
    static String cppScript(Language language, boolean coverage) {
        String flags = coverage ? "-std=c++17 -O0 --coverage" : "-std=c++17";
        String solutionPattern = language.getSolutionFileName().replace(".", "\\.");
        String coverageStep = coverage
                ? "echo '" + MARKER + "COVERAGE_BEGIN'; gcovr -r /work --filter '.*" + solutionPattern + "$' -s 2>&1;"
                : "";
        return "( g++ " + flags + " -o test_runner test.cpp > compile.log 2>&1; ec=$?; "
                + "echo '" + MARKER + "COMPILE_EXIT='$ec; cat compile.log; "
                + "echo '" + MARKER + "RUN_BEGIN'; "
                + "if [ $ec -eq 0 ]; then ./test_runner 2>&1; echo '" + MARKER + "RUN_EXIT='$?; "
                + coverageStep + " fi ) 2>&1 | head -c " + MAX_CAPTURE_BYTES;
    }

    /**
     * Shell script for Python: parse both files with {@code ast} (syntax check without writing
     * bytecode), then run pytest with the {@code -rA} summary.
     */
    static String pythonScript(boolean coverage) {
        String covFlags = coverage ? " --cov=solution --cov-report=term" : "";
        return "( python -c 'import ast,sys; [ast.parse(open(f).read(), f) for f in sys.argv[1:]]' "
                + "solution.py test_solution.py > compile.log 2>&1; ec=$?; "
                + "echo '" + MARKER + "COMPILE_EXIT='$ec; cat compile.log; "
                + "echo '" + MARKER + "RUN_BEGIN'; "
                + "if [ $ec -eq 0 ]; then pytest -q -rA --tb=short -p no:cacheprovider" + covFlags
                + " test_solution.py 2>&1; echo '" + MARKER + "RUN_EXIT='$?; fi ) 2>&1 | head -c " + MAX_CAPTURE_BYTES;
    }

    /**
     * Runs the docker command, feeding {@code stdin} to the container and writing combined
     * output to {@code outputFile}.
     *
     * @return true if the run timed out and the container was killed
     */
    private boolean runProcess(List<String> command, String containerName, byte[] stdin, Path outputFile)
            throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(outputFile.toFile())
                .start();
        try (OutputStream in = process.getOutputStream()) {
            in.write(stdin);
        } catch (IOException e) {
            // Container exited before reading its input (e.g. image missing); the output file explains why.
            log.warn("Sandbox {} closed stdin early: {}", containerName, e.getMessage());
        }
        int timeoutSeconds = properties.sandbox().timeoutSeconds();
        if (process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            return false;
        }
        log.warn("Sandbox {} exceeded {} s, killing container", containerName, timeoutSeconds);
        killContainer(containerName);
        process.destroyForcibly();
        process.waitFor(5, TimeUnit.SECONDS);
        return true;
    }

    private void killContainer(String containerName) throws InterruptedException {
        try {
            Process kill = new ProcessBuilder("docker", "kill", containerName)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!kill.waitFor(10, TimeUnit.SECONDS)) {
                kill.destroyForcibly();
            }
        } catch (IOException e) {
            log.error("Failed to kill sandbox container {}: {}", containerName, e.getMessage());
        }
    }

    // ── Parsing ───────────────────────────────────────────────

    static SandboxResult parsePython(String output, boolean timedOut) {
        Phases phases = Phases.split(output);
        Integer runExit = phases.runExit();
        boolean compiled = phases.compileExit() != null && phases.compileExit() == 0
                && (runExit == null || !PYTEST_NOT_RUNNABLE.contains(runExit));
        String compileOutput = phases.compileExit() != null && phases.compileExit() != 0
                ? phases.compileOutput()
                : compiled ? "" : phases.runOutput();

        int passed = 0;
        List<TestFailure> failures = new ArrayList<>();
        if (compiled) {
            Matcher m = PYTEST_RESULT.matcher(phases.runOutput());
            while (m.find()) {
                String name = testName(m.group(2));
                if (m.group(1).equals("PASSED")) {
                    passed++;
                } else {
                    failures.add(new TestFailure(name, truncate(m.group(3), MAX_FAILURE_MESSAGE_CHARS)));
                }
            }
        }

        Double coverage = null;
        Matcher cov = PYTEST_COVERAGE.matcher(phases.runOutput());
        if (cov.find()) {
            coverage = Double.parseDouble(cov.group(1));
        }
        return result(compiled, compileOutput, passed, failures, timedOut, output, coverage);
    }

    static SandboxResult parseCpp(String output, boolean timedOut) {
        Phases phases = Phases.split(output);
        boolean compiled = phases.compileExit() != null && phases.compileExit() == 0;
        String runOutput = phases.runOutput();
        String testOutput = runOutput.contains(MARKER + "COVERAGE_BEGIN")
                ? runOutput.substring(0, runOutput.indexOf(MARKER + "COVERAGE_BEGIN"))
                : runOutput;

        int passed = 0;
        List<TestFailure> failures = new ArrayList<>();
        if (compiled) {
            Matcher pass = CPP_PASS.matcher(testOutput);
            while (pass.find()) {
                passed++;
            }
            Matcher fail = CPP_FAIL.matcher(testOutput);
            while (fail.find()) {
                failures.add(new TestFailure(fail.group(1), truncate(fail.group(2), MAX_FAILURE_MESSAGE_CHARS)));
            }
            Integer runExit = phases.runExit();
            boolean crashed = runExit == null || (runExit != 0 && failures.isEmpty());
            if (passed == 0 && failures.isEmpty()) {
                // No PASS/FAIL protocol lines: fall back to the exit code as a single test.
                if (runExit != null && runExit == 0) {
                    passed = 1;
                } else {
                    failures.add(new TestFailure("main", exitMessage(runExit, timedOut, testOutput)));
                }
            } else if (crashed) {
                failures.add(new TestFailure("main", exitMessage(runExit, timedOut, testOutput)));
            }
        }

        Double coverage = null;
        Matcher cov = GCOVR_LINES.matcher(runOutput);
        if (cov.find()) {
            coverage = Double.parseDouble(cov.group(1));
        }
        String compileOutput = compiled ? "" : phases.compileOutput();
        return result(compiled, compileOutput, passed, failures, timedOut, output, coverage);
    }

    private static SandboxResult result(boolean compiled, String compileOutput, int passed,
                                        List<TestFailure> failures, boolean timedOut,
                                        String output, Double coverage) {
        return new SandboxResult(compiled, truncate(compileOutput, MAX_OUTPUT_CHARS),
                passed, failures.size(), passed + failures.size(), List.copyOf(failures),
                timedOut, truncate(output, MAX_OUTPUT_CHARS), coverage);
    }

    private static String exitMessage(Integer runExit, boolean timedOut, String testOutput) {
        String reason = timedOut ? "timed out"
                : runExit == null ? "terminated without exit code (output limit or crash)"
                : "exited with code " + runExit;
        String tail = testOutput.length() > MAX_FAILURE_MESSAGE_CHARS
                ? testOutput.substring(testOutput.length() - MAX_FAILURE_MESSAGE_CHARS)
                : testOutput;
        return truncate(reason + (tail.isBlank() ? "" : ": " + tail.strip()), MAX_FAILURE_MESSAGE_CHARS);
    }

    /** {@code test_solution.py::test_add[1-2]} → {@code test_add[1-2]}. */
    private static String testName(String nodeId) {
        int idx = nodeId.lastIndexOf("::");
        return idx >= 0 ? nodeId.substring(idx + 2) : nodeId;
    }

    static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "\n...[truncated]";
    }

    /** Output split by marker lines into compile and run phases. */
    private record Phases(Integer compileExit, String compileOutput, Integer runExit, String runOutput) {

        static Phases split(String output) {
            Integer compileExit = findInt(COMPILE_EXIT, output);
            Integer runExit = findInt(RUN_EXIT, output);
            String runBegin = MARKER + "RUN_BEGIN";
            int compileStart = output.indexOf(MARKER + "COMPILE_EXIT=");
            int runStart = output.indexOf(runBegin);

            String compileOutput = "";
            if (compileStart >= 0) {
                int lineEnd = output.indexOf('\n', compileStart);
                int end = runStart >= 0 ? runStart : output.length();
                compileOutput = lineEnd >= 0 && lineEnd < end ? output.substring(lineEnd + 1, end).strip() : "";
            }
            String runOutput = runStart >= 0 ? output.substring(runStart + runBegin.length()) : "";
            runOutput = RUN_EXIT.matcher(runOutput).replaceAll("").strip();
            return new Phases(compileExit, compileOutput, runExit, runOutput);
        }

        private static Integer findInt(Pattern pattern, String text) {
            Matcher m = pattern.matcher(text);
            return m.find() ? Integer.parseInt(m.group(1)) : null;
        }
    }

    // ── Files ─────────────────────────────────────────────────

    private static String readCapped(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] bytes = in.readNBytes(MAX_CAPTURE_BYTES);
            return new String(bytes, StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Could not delete sandbox output file {}: {}", path, e.getMessage());
        }
    }
}
