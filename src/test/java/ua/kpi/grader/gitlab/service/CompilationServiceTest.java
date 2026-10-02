package ua.kpi.grader.gitlab.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import ua.kpi.grader.course.entity.Language;
import ua.kpi.grader.submission.feedback.GraderHarness;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIf("ua.kpi.grader.gitlab.service.CompilationServiceTest#gppAvailable")
class CompilationServiceTest {

    private final CompilationService service = new CompilationService(new GraderHarness());

    @Test
    void compileSolutionWithTests_harnessTestFile_compiles() {
        String tests = """
                #include "grader_test.h"
                #include "solution.cpp"

                TEST_CASE(test_add) {
                    EXPECT_EQ(5, add(2, 3));
                }
                """;

        CompilationResult result = service.compileSolutionWithTests(
                "int add(int a, int b) { return a + b; }\n", tests, Language.CPP);

        assertThat(result.success()).as(result.output()).isTrue();
    }

    @Test
    void compileSolutionWithTests_brokenSolution_reportsError() {
        String tests = "#include \"grader_test.h\"\n#include \"solution.cpp\"\nTEST_CASE(t) { EXPECT_EQ(1, f()); }\n";

        CompilationResult result = service.compileSolutionWithTests("int f() { return 1 }\n", tests, Language.CPP);

        assertThat(result.success()).isFalse();
        assertThat(result.output()).contains("error");
    }

    static boolean gppAvailable() {
        try {
            Process process = new ProcessBuilder("g++", "--version")
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            return process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
