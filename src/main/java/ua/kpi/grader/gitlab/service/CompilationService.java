package ua.kpi.grader.gitlab.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ua.kpi.grader.course.entity.Language;
import ua.kpi.grader.testgen.sandbox.CompileCheck;
import ua.kpi.grader.testgen.sandbox.SandboxRunner;
import ua.kpi.grader.testgen.sandbox.SandboxUnavailableException;

/**
 * Validates solution compilation/syntax per language before pushing to GitLab CI.
 *
 * <p>The compiler runs in the network-less sandbox container ({@link SandboxRunner}), never in
 * the backend process: student code could otherwise read backend files through the compiler
 * (e.g. {@code #include "/proc/self/environ"}), and the backend image has no compilers anyway.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompilationService {

    private final SandboxRunner sandboxRunner;

    /**
     * Compiles a solution file together with a test file (unit test mode), with the grader
     * harness next to them exactly as in the CI run.
     *
     * @param solutionContent the student's or teacher's solution code
     * @param testFileContent the test file content with assertions
     * @param language        target language
     * @return compilation result with success status and any error output
     */
    public CompilationResult compileSolutionWithTests(String solutionContent,
                                                      String testFileContent,
                                                      Language language) {
        return check(language, solutionContent, testFileContent);
    }

    /**
     * Compiles a single solution file — syntax check only.
     *
     * @param solutionContent the code to validate
     * @param language        target language
     * @return compilation result
     */
    public CompilationResult compileSolution(String solutionContent, Language language) {
        return check(language, solutionContent, null);
    }

    private CompilationResult check(Language language, String solution, String testFile) {
        try {
            CompileCheck result = sandboxRunner.compileOnly(language, solution, testFile);
            return new CompilationResult(result.success(), result.output());
        } catch (SandboxUnavailableException e) {
            log.error("Compilation check failed: {}", e.getMessage(), e);
            return new CompilationResult(false, "Internal error: " + e.getMessage());
        }
    }
}
