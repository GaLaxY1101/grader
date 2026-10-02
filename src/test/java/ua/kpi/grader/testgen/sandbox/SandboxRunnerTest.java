package ua.kpi.grader.testgen.sandbox;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import ua.kpi.grader.course.entity.Language;
import ua.kpi.grader.submission.feedback.GraderHarness;
import ua.kpi.grader.submission.feedback.TestCaseResult;
import ua.kpi.grader.submission.feedback.TestCaseStatus;
import ua.kpi.grader.submission.feedback.TestReport;
import ua.kpi.grader.submission.feedback.TestReportParser;
import ua.kpi.grader.submission.feedback.TestRunStatus;
import ua.kpi.grader.testgen.config.TestGenProperties;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class SandboxRunnerTest {

    private static final String PY_SOLUTION = "def add(a, b):\n    return a + b\n";
    private static final String CPP_SOLUTION = "int add(int a, int b) { return a + b; }\n";

    private static SandboxRunner runner(int timeoutSeconds) {
        TestGenProperties properties = new TestGenProperties(
                new TestGenProperties.Ollama("http://localhost:11434", "qwen2.5-coder:3b", 180, "30m", 8192),
                0.2, 3, 5, true, 10, true,
                new TestGenProperties.Sandbox("grader-sandbox-cpp:1", "grader-sandbox-py:1",
                        timeoutSeconds, 5, "256m", "1"));
        return new SandboxRunner(properties, new GraderHarness());
    }

    /** Parsing tests on captured output; no Docker needed. */
    @Nested
    class Parsing {

        @Test
        void parsePython_countsPassedAndFailedFromSummary() {
            String output = """
                    @@SANDBOX:COMPILE_EXIT=0
                    @@SANDBOX:RUN_BEGIN
                    .F
                    PASSED test_solution.py::test_ok
                    FAILED test_solution.py::test_bad[1-2] - AssertionError: wrong sum
                    1 failed, 1 passed in 0.05s
                    @@SANDBOX:RUN_EXIT=1
                    """;

            SandboxResult result = SandboxRunner.parsePython(output, false);

            assertThat(result.compiled()).isTrue();
            assertThat(result.passed()).isEqualTo(1);
            assertThat(result.failed()).isEqualTo(1);
            assertThat(result.failures().get(0).name()).isEqualTo("test_bad[1-2]");
            assertThat(result.failures().get(0).message()).isEqualTo("AssertionError: wrong sum");
        }

        @Test
        void parseCpp_crashAfterPassesAddsSyntheticFailure() {
            String output = """
                    @@SANDBOX:COMPILE_EXIT=0
                    @@SANDBOX:RUN_BEGIN
                    PASS test_one
                    @@SANDBOX:RUN_EXIT=139
                    """;

            SandboxResult result = SandboxRunner.parseCpp(output, false);

            assertThat(result.passed()).isEqualTo(1);
            assertThat(result.failed()).isEqualTo(1);
            assertThat(result.failures().get(0).message()).contains("exited with code 139");
        }

        @Test
        void truncate_capsLongOutput() {
            String longText = "x".repeat(SandboxRunner.MAX_OUTPUT_CHARS + 100);

            assertThat(SandboxRunner.truncate(longText, SandboxRunner.MAX_OUTPUT_CHARS))
                    .hasSizeLessThan(SandboxRunner.MAX_OUTPUT_CHARS + 30)
                    .endsWith("[truncated]");
        }
    }

    /** Runs real containers; needs Docker and the images from {@code sandbox/build.sh}. */
    @Nested
    @Tag("docker")
    @EnabledIf("ua.kpi.grader.testgen.sandbox.SandboxRunnerTest#sandboxImagesAvailable")
    class Docker {

        private final SandboxRunner runner = runner(20);

        @Test
        void python_passingTests() {
            String tests = """
                    from solution import *

                    def test_positive():
                        assert add(2, 3) == 5

                    def test_negative():
                        assert add(-2, -3) == -5
                    """;

            SandboxResult result = runner.run(Language.PYTHON, PY_SOLUTION, tests);

            assertThat(result.compiled()).isTrue();
            assertThat(result.passed()).isEqualTo(2);
            assertThat(result.failed()).isZero();
            assertThat(result.allPassed()).isTrue();
            assertThat(result.timedOut()).isFalse();
        }

        @Test
        void python_failingTestIsReportedWithMessage() {
            String tests = """
                    from solution import *

                    def test_ok():
                        assert add(2, 3) == 5

                    def test_wrong():
                        assert add(2, 3) == 6, "expected six"
                    """;

            SandboxResult result = runner.run(Language.PYTHON, PY_SOLUTION, tests);

            assertThat(result.compiled()).isTrue();
            assertThat(result.passed()).isEqualTo(1);
            assertThat(result.failed()).isEqualTo(1);
            assertThat(result.failures()).singleElement()
                    .satisfies(f -> {
                        assertThat(f.name()).isEqualTo("test_wrong");
                        assertThat(f.message()).contains("expected six");
                    });
        }

        @Test
        void python_syntaxErrorIsCompileFailure() {
            String tests = "from solution import *\n\ndef test_broken(:\n    assert True\n";

            SandboxResult result = runner.run(Language.PYTHON, PY_SOLUTION, tests);

            assertThat(result.compiled()).isFalse();
            assertThat(result.compileOutput()).contains("SyntaxError");
            assertThat(result.total()).isZero();
        }

        @Test
        void python_importErrorIsCompileFailure() {
            String tests = "from solution import multiply\n\ndef test_mul():\n    assert multiply(2, 3) == 6\n";

            SandboxResult result = runner.run(Language.PYTHON, PY_SOLUTION, tests);

            assertThat(result.compiled()).isFalse();
            assertThat(result.compileOutput()).contains("ImportError");
        }

        @Test
        void cpp_compileErrorIsReported() {
            String tests = """
                    #include <cstdio>
                    #include "solution.cpp"
                    int main() { return add(1, 2) == 3 ? 0 : 1 }
                    """;

            SandboxResult result = runner.run(Language.CPP, CPP_SOLUTION, tests);

            assertThat(result.compiled()).isFalse();
            assertThat(result.compileOutput()).contains("error");
            assertThat(result.total()).isZero();
        }

        @Test
        void cpp_parsesPassAndFailLines() {
            String tests = """
                    #include <cstdio>
                    #include "solution.cpp"

                    int check(const char* name, int expected, int actual) {
                        if (expected == actual) { std::printf("PASS %s\\n", name); return 0; }
                        std::printf("FAIL %s: expected %d got %d\\n", name, expected, actual);
                        return 1;
                    }

                    int main() {
                        int failures = 0;
                        failures += check("test_positive", 5, add(2, 3));
                        failures += check("test_zero", 0, add(0, 0));
                        failures += check("test_wrong", 7, add(2, 3));
                        return failures;
                    }
                    """;

            SandboxResult result = runner.run(Language.CPP, CPP_SOLUTION, tests);

            assertThat(result.compiled()).isTrue();
            assertThat(result.passed()).isEqualTo(2);
            assertThat(result.failed()).isEqualTo(1);
            assertThat(result.failures()).singleElement()
                    .satisfies(f -> {
                        assertThat(f.name()).isEqualTo("test_wrong");
                        assertThat(f.message()).isEqualTo("expected 7 got 5");
                    });
        }

        @Test
        void cpp_fallsBackToExitCodeWithoutProtocolLines() {
            String tests = """
                    #include "solution.cpp"
                    int main() { return add(2, 3) == 5 ? 0 : 1; }
                    """;

            SandboxResult result = runner.run(Language.CPP, CPP_SOLUTION, tests);

            assertThat(result.compiled()).isTrue();
            assertThat(result.passed()).isEqualTo(1);
            assertThat(result.failed()).isZero();
        }

        @Test
        void c_solutionIsIncludedIntoCppTest() {
            String tests = """
                    #include <cstdio>
                    #include "solution.c"
                    int main() {
                        if (add(1, 1) == 2) { std::puts("PASS test_add"); return 0; }
                        std::puts("FAIL test_add: expected 2"); return 1;
                    }
                    """;

            SandboxResult result = runner.run(Language.C, CPP_SOLUTION, tests);

            assertThat(result.allPassed()).isTrue();
        }

        @Test
        void infiniteLoopTimesOut() {
            SandboxRunner fastRunner = runner(5);
            String tests = """
                    from solution import *

                    def test_forever():
                        while True:
                            pass
                    """;

            long start = System.nanoTime();
            SandboxResult result = fastRunner.run(Language.PYTHON, PY_SOLUTION, tests);
            long seconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start);

            assertThat(result.timedOut()).isTrue();
            assertThat(result.allPassed()).isFalse();
            assertThat(seconds).isLessThan(20);
        }

        @Test
        void python_mutantBatch_reportsKilledSurvivingAndLoopingMutants() {
            String tests = "from solution import *\n\ndef test_add():\n    assert add(2, 3) == 5\n";
            List<String> mutants = List.of(
                    "def add(a, b):\n    return a - b\n",
                    "def add(a, b):\n    return b + a\n",
                    "def add(a, b):\n    while True:\n        pass\n");

            List<SandboxResult> results = runner.runAgainstMutants(Language.PYTHON, tests, mutants);

            assertThat(results).hasSize(3);
            assertThat(results.get(0).allPassed()).as("a - b is killed").isFalse();
            assertThat(results.get(1).allPassed()).as("b + a is equivalent and survives").isTrue();
            assertThat(results.get(2).timedOut()).as("infinite loop is cut off").isTrue();
            assertThat(results.get(2).allPassed()).isFalse();
        }

        @Test
        void cpp_mutantBatch_recompilesEachMutant() {
            String tests = """
                    #include <iostream>
                    #include "solution.cpp"
                    int main() {
                        if (add(2, 3) == 5) { std::cout << "PASS test_add" << std::endl; return 0; }
                        std::cout << "FAIL test_add: expected 5" << std::endl; return 1;
                    }
                    """;
            List<String> mutants = List.of(
                    "int add(int a, int b) { return a - b; }\n",
                    "int add(int a, int b) { return a + b; }\n");

            List<SandboxResult> results = runner.runAgainstMutants(Language.CPP, tests, mutants);

            assertThat(results).extracting(SandboxResult::allPassed).containsExactly(false, true);
        }

        @Test
        void checkSyntax_flagsBrokenSources() {
            assertThat(runner.checkSyntax(Language.PYTHON, List.of(
                    "def f(a):\n    return a < 1\n", "def f(a):\n    return a <\n")))
                    .containsExactly(true, false);
            assertThat(runner.checkSyntax(Language.CPP, List.of(
                    "#include <vector>\nint f(std::vector<int> v) { return v.size(); }\n",
                    "#include <vector>\nint f(std::vector<=int> v) { return v.size(); }\n")))
                    .containsExactly(true, false);
        }

        @Test
        void python_coverageIsMeasured() {
            String solution = "def sign(x):\n    if x > 0:\n        return 1\n    return -1\n";
            String tests = "from solution import *\n\ndef test_pos():\n    assert sign(5) == 1\n";

            SandboxResult result = runner.runWithCoverage(Language.PYTHON, solution, tests);

            assertThat(result.allPassed()).isTrue();
            assertThat(result.coveragePct()).isNotNull().isGreaterThan(0.0).isLessThan(100.0);
        }

        @Test
        void cpp_coverageIsMeasured() {
            String solution = "int sign(int x) {\n    if (x > 0) {\n        return 1;\n    }\n    return -1;\n}\n";
            String tests = """
                    #include <cstdio>
                    #include "solution.cpp"
                    int main() {
                        if (sign(5) == 1) { std::puts("PASS test_pos"); return 0; }
                        std::puts("FAIL test_pos: expected 1"); return 1;
                    }
                    """;

            SandboxResult result = runner.runWithCoverage(Language.CPP, solution, tests);

            assertThat(result.allPassed()).isTrue();
            assertThat(result.coveragePct()).isNotNull().isGreaterThan(0.0).isLessThan(100.0);
        }
    }

    /** The grader harness (grader_test.h / conftest.py) run end to end in the sandbox images. */
    @Nested
    @Tag("docker")
    @EnabledIf("ua.kpi.grader.testgen.sandbox.SandboxRunnerTest#sandboxImagesAvailable")
    class Harness {

        private final SandboxRunner runner = runner(20);

        private static final String CPP_HARNESS_SOLUTION = """
                #include <stdexcept>
                #include <string>
                #include <vector>
                int add(int a, int b) { return a + b; }
                bool isPal(const std::string& s) { return s.size() < 2; }
                std::vector<int> firstThree() { return {1, 2, 3}; }
                int boom() { throw std::runtime_error("bad input"); }
                int crash() { volatile int* p = nullptr; return *p; }
                void spin() { for (volatile int i = 0; ; i = i + 1) {} }
                """;

        private static TestReport report(SandboxResult result) {
            return TestReportParser.parse(result.rawOutput());
        }

        @Test
        void cpp_passingFailingAndExceptionWithValues() {
            String tests = """
                    #include "grader_test.h"
                    #include "solution.cpp"

                    TEST_CASE(test_add) { EXPECT_EQ(5, add(2, 3)); }
                    TEST_CASE(test_mixed_case) { EXPECT_EQ(true, isPal("Abba")); }
                    TEST_CASE(test_vector) { EXPECT_EQ((std::vector<int>{1, 2, 4}), firstThree()); }
                    TEST_CASE(test_string) { EXPECT_EQ("abc", std::string("abd")); }
                    TEST_CASE(test_exception) { boom(); }
                    """;

            SandboxResult result = runner.run(Language.CPP, CPP_HARNESS_SOLUTION, tests);
            TestReport report = report(result);

            assertThat(result.compiled()).isTrue();
            assertThat(result.passed()).isEqualTo(1);
            assertThat(result.failed()).isEqualTo(4);
            assertThat(report.status()).isEqualTo(TestRunStatus.COMPLETED);
            assertThat(report.tests()).extracting(TestCaseResult::status).containsExactly(
                    TestCaseStatus.PASSED, TestCaseStatus.FAILED, TestCaseStatus.FAILED,
                    TestCaseStatus.FAILED, TestCaseStatus.ERROR);
            assertThat(report.tests().get(1)).extracting(TestCaseResult::expected, TestCaseResult::actual)
                    .containsExactly("true", "false");
            assertThat(report.tests().get(2)).extracting(TestCaseResult::expected, TestCaseResult::actual)
                    .containsExactly("[1, 2, 4]", "[1, 2, 3]");
            assertThat(report.tests().get(3)).extracting(TestCaseResult::expected, TestCaseResult::actual)
                    .containsExactly("\"abc\"", "\"abd\"");
            assertThat(report.tests().get(4).message()).contains("bad input");
            assertThat(result.failures().getFirst().message()).isEqualTo("expected true, got false (EXPECT_EQ(true, isPal(\"Abba\")))");
        }

        @Test
        void cpp_segfaultMarksCrashedAndNotRun() {
            String tests = """
                    #include "grader_test.h"
                    #include "solution.cpp"

                    TEST_CASE(test_ok) { EXPECT_EQ(5, add(2, 3)); }
                    TEST_CASE(test_crash) { EXPECT_EQ(0, crash()); }
                    TEST_CASE(test_after) { EXPECT_EQ(2, add(1, 1)); }
                    """;

            TestReport report = report(runner.run(Language.CPP, CPP_HARNESS_SOLUTION, tests));

            assertThat(report.status()).isEqualTo(TestRunStatus.CRASHED);
            assertThat(report.tests()).extracting(TestCaseResult::status).containsExactly(
                    TestCaseStatus.PASSED, TestCaseStatus.CRASHED, TestCaseStatus.NOT_RUN);
        }

        @Test
        void cpp_infiniteLoopLeavesTestUnfinished() {
            String tests = """
                    #include "grader_test.h"
                    #include "solution.cpp"

                    TEST_CASE(test_ok) { EXPECT_EQ(5, add(2, 3)); }
                    TEST_CASE(test_loop) { spin(); }
                    """;

            // Per-run "timeout -s KILL" (as in the mutant batch) keeps the output printed before the kill.
            SandboxResult result = runner.runAgainstMutants(Language.CPP, tests, List.of(CPP_HARNESS_SOLUTION))
                    .getFirst();
            TestReport report = report(result);

            assertThat(result.timedOut()).isTrue();
            assertThat(result.allPassed()).isFalse();
            assertThat(report.tests()).extracting(TestCaseResult::status)
                    .containsExactly(TestCaseStatus.PASSED, TestCaseStatus.CRASHED);
        }

        @Test
        void cpp_compileError() {
            String tests = """
                    #include "grader_test.h"
                    #include "solution.cpp"

                    TEST_CASE(test_add) { EXPECT_EQ(5, add(2, 3)) }
                    """;

            SandboxResult result = runner.run(Language.CPP, CPP_HARNESS_SOLUTION, tests);

            assertThat(result.compiled()).isFalse();
            assertThat(result.compileOutput()).contains("error");
        }

        @Test
        void c_solutionWithHarness() {
            String tests = """
                    #include "grader_test.h"
                    #include "solution.c"

                    TEST_CASE(test_add) { EXPECT_EQ(2, add(1, 1)); }
                    """;

            SandboxResult result = runner.run(Language.C, CPP_SOLUTION, tests);

            assertThat(result.allPassed()).isTrue();
            assertThat(report(result).fromEvents()).isTrue();
        }

        @Test
        void python_failingWithValuesAndException() {
            String solution = PY_SOLUTION + "def boom():\n    raise ValueError('bad input')\n";
            String tests = """
                    from solution import *

                    def test_ok():
                        assert add(2, 3) == 5

                    def test_list():
                        actual = [add(1, 0), add(1, 1), add(1, 2)]
                        assert actual == [1, 2, 4]

                    def test_string():
                        assert str(add(1, 1)) == "3"

                    def test_exception():
                        boom()
                    """;

            SandboxResult result = runner.run(Language.PYTHON, solution, tests);
            TestReport report = report(result);

            assertThat(result.compiled()).isTrue();
            assertThat(result.passed()).isEqualTo(1);
            assertThat(report.status()).isEqualTo(TestRunStatus.COMPLETED);
            assertThat(report.tests()).extracting(TestCaseResult::status).containsExactly(
                    TestCaseStatus.PASSED, TestCaseStatus.FAILED, TestCaseStatus.FAILED, TestCaseStatus.ERROR);
            assertThat(report.tests().get(1)).extracting(TestCaseResult::expected, TestCaseResult::actual)
                    .containsExactly("[1, 2, 4]", "[1, 2, 3]");
            assertThat(report.tests().get(2)).extracting(TestCaseResult::expected, TestCaseResult::actual)
                    .containsExactly("'3'", "'2'");
            assertThat(report.tests().get(3).message()).isEqualTo("ValueError: bad input");
        }

        @Test
        void python_importErrorIsCompileFailure() {
            String tests = "from solution import *\n\ndef test_ok():\n    assert add(1, 2) == 3\n";

            SandboxResult result = runner.run(Language.PYTHON, "import missing_module\n" + PY_SOLUTION, tests);

            assertThat(result.compiled()).isFalse();
            assertThat(result.compileOutput()).contains("ModuleNotFoundError").doesNotContain("site-packages");
        }

        @Test
        void python_infiniteLoopLeavesTestUnfinished() {
            String tests = """
                    from solution import *

                    def test_ok():
                        assert add(2, 3) == 5

                    def test_forever():
                        while True:
                            pass
                    """;

            SandboxResult result = runner.runAgainstMutants(Language.PYTHON, tests, List.of(PY_SOLUTION)).getFirst();
            TestReport report = report(result);

            assertThat(result.timedOut()).isTrue();
            assertThat(report.tests()).extracting(TestCaseResult::status)
                    .containsExactly(TestCaseStatus.PASSED, TestCaseStatus.CRASHED);
        }
    }

    static boolean sandboxImagesAvailable() {
        try {
            Process process = new ProcessBuilder("docker", "image", "inspect",
                    "grader-sandbox-py:1", "grader-sandbox-cpp:1")
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            return process.waitFor(15, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
