package ua.kpi.grader.gitlab.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ua.kpi.grader.course.entity.Language;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Validates solution compilation/syntax locally per language.
 * Used to provide early feedback before pushing to GitLab CI.
 */
@Slf4j
@Service
public class CompilationService {

    private static final int TIMEOUT_SECONDS = 15;

    /**
     * Compiles a solution file together with a test file (unit test mode).
     * Writes both files to a temp directory and invokes the appropriate
     * syntax-check command for the target language.
     *
     * @param solutionContent the student's or teacher's solution code
     * @param testFileContent the test file content with assertions
     * @param language        target language
     * @return compilation result with success status and any error output
     */
    public CompilationResult compileSolutionWithTests(String solutionContent,
                                                      String testFileContent,
                                                      Language language) {
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("grader-compile-");
            Path solutionFile = tempDir.resolve(language.getSolutionFileName());
            Path testFile = tempDir.resolve(language.getTestFileName());

            Files.writeString(solutionFile, solutionContent);
            Files.writeString(testFile, testFileContent);

            List<String> command = buildCheckCommand(language, true);
            return runProcess(tempDir, command);

        } catch (IOException | InterruptedException e) {
            log.error("Compilation check failed: {}", e.getMessage(), e);
            return new CompilationResult(false, "Internal error: " + e.getMessage());
        } finally {
            cleanupTempDir(tempDir);
        }
    }

    /**
     * Compiles a single solution file — syntax check only.
     *
     * @param solutionContent the code to validate
     * @param language        target language
     * @return compilation result
     */
    public CompilationResult compileSolution(String solutionContent, Language language) {
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("grader-compile-");
            Path solutionFile = tempDir.resolve(language.getSolutionFileName());

            Files.writeString(solutionFile, solutionContent);

            List<String> command = buildCheckCommand(language, false);
            return runProcess(tempDir, command);

        } catch (IOException | InterruptedException e) {
            log.error("Compilation check failed: {}", e.getMessage(), e);
            return new CompilationResult(false, "Internal error: " + e.getMessage());
        } finally {
            cleanupTempDir(tempDir);
        }
    }

    private List<String> buildCheckCommand(Language language, boolean withTests) {
        return switch (language) {
            case C, CPP -> {
                String target = withTests ? language.getTestFileName() : language.getSolutionFileName();
                yield List.of("g++", "-fsyntax-only", "-std=c++17", target);
            }
            case PYTHON -> {
                if (withTests) {
                    yield List.of("python", "-m", "py_compile",
                            language.getSolutionFileName(), language.getTestFileName());
                }
                yield List.of("python", "-m", "py_compile", language.getSolutionFileName());
            }
        };
    }

    private CompilationResult runProcess(Path tempDir, List<String> command)
            throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(tempDir.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes());
        boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        if (!finished) {
            process.destroyForcibly();
            return new CompilationResult(false, "Compilation timed out");
        }

        int exitCode = process.exitValue();
        return new CompilationResult(exitCode == 0, output.isBlank() ? null : output.strip());
    }

    private void cleanupTempDir(Path tempDir) {
        if (tempDir == null) return;
        try {
            Files.walk(tempDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }
}
