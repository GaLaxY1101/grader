package ua.kpi.grader.gitlab.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Validates C++ code compilation by running g++ locally.
 * Used to provide early feedback before pushing to GitLab CI.
 */
@Slf4j
@Service
public class CompilationService {

    private static final int TIMEOUT_SECONDS = 15;

    /**
     * Compiles a solution file together with a test file (unit test mode).
     * Writes both files to a temp directory and runs g++ on test.cpp
     * (which is expected to #include "solution.cpp").
     *
     * @param solutionContent the student's or teacher's solution code
     * @param testFileContent the test file content with assertions
     * @return compilation result with success status and any error output
     */
    public CompilationResult compileSolutionWithTests(String solutionContent, String testFileContent) {
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("grader-compile-");
            Path solutionFile = tempDir.resolve("solution.cpp");
            Path testFile = tempDir.resolve("test.cpp");

            Files.writeString(solutionFile, solutionContent);
            Files.writeString(testFile, testFileContent);

            ProcessBuilder pb = new ProcessBuilder(
                    "g++", "-fsyntax-only", "-std=c++17", "test.cpp"
            );
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

        } catch (IOException | InterruptedException e) {
            log.error("Compilation check failed: {}", e.getMessage(), e);
            return new CompilationResult(false, "Internal error: " + e.getMessage());
        } finally {
            cleanupTempDir(tempDir);
        }
    }

    /**
     * Compiles a single solution file (IO mode — just checks syntax).
     *
     * @param solutionContent the code to validate
     * @return compilation result
     */
    public CompilationResult compileSolution(String solutionContent) {
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("grader-compile-");
            Path solutionFile = tempDir.resolve("solution.cpp");

            Files.writeString(solutionFile, solutionContent);

            ProcessBuilder pb = new ProcessBuilder(
                    "g++", "-fsyntax-only", "-std=c++17", "solution.cpp"
            );
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

        } catch (IOException | InterruptedException e) {
            log.error("Compilation check failed: {}", e.getMessage(), e);
            return new CompilationResult(false, "Internal error: " + e.getMessage());
        } finally {
            cleanupTempDir(tempDir);
        }
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
