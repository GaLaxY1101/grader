package ua.kpi.grader.submission.feedback;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestReportParserTest {

    private static final String P = TestReportParser.PREFIX;

    private static String plan(String... names) {
        StringBuilder sb = new StringBuilder(P + "{\"event\":\"plan\",\"tests\":[");
        for (int i = 0; i < names.length; i++) {
            sb.append(i > 0 ? "," : "").append('"').append(names[i]).append('"');
        }
        return sb.append("]}\n").toString();
    }

    private static String start(String name) {
        return P + "{\"event\":\"start\",\"name\":\"" + name + "\"}\n";
    }

    private static String passed(String name) {
        return P + "{\"event\":\"result\",\"name\":\"" + name + "\",\"status\":\"PASSED\",\"durationMs\":1}\n";
    }

    private static String failed(String name, String expected, String actual) {
        return P + "{\"event\":\"result\",\"name\":\"" + name + "\",\"status\":\"FAILED\",\"expected\":\""
                + expected + "\",\"actual\":\"" + actual + "\",\"message\":\"EXPECT_EQ\",\"durationMs\":0}\n";
    }

    private static final String END = P + "{\"event\":\"end\"}\n";

    @Nested
    class Events {

        @Test
        void allStatuses_completedRun() {
            String log = plan("test_a", "test_b", "test_c")
                    + start("test_a") + passed("test_a") + "PASS test_a\n"
                    + start("test_b") + failed("test_b", "true", "false") + "FAIL test_b: expected true got false\n"
                    + start("test_c")
                    + P + "{\"event\":\"result\",\"name\":\"test_c\",\"status\":\"ERROR\",\"message\":\"exception: bad\"}\n"
                    + END;

            TestReport report = TestReportParser.parse(log);

            assertThat(report.status()).isEqualTo(TestRunStatus.COMPLETED);
            assertThat(report.detailsAvailable()).isTrue();
            assertThat(report.fromEvents()).isTrue();
            assertThat(report.passed()).isEqualTo(1);
            assertThat(report.total()).isEqualTo(3);
            assertThat(report.tests()).extracting(TestCaseResult::status)
                    .containsExactly(TestCaseStatus.PASSED, TestCaseStatus.FAILED, TestCaseStatus.ERROR);
            TestCaseResult b = report.tests().get(1);
            assertThat(b.expected()).isEqualTo("true");
            assertThat(b.actual()).isEqualTo("false");
            assertThat(report.tests().get(0).durationMs()).isEqualTo(1L);
            assertThat(report.tests().get(2).message()).isEqualTo("exception: bad");
        }

        @Test
        void crashMidRun_marksCrashedAndNotRun() {
            String log = plan("t1", "t2", "t3") + start("t1") + passed("t1") + start("t2")
                    + "Segmentation fault (core dumped)\nERROR: Job failed: exit code 139\n";

            TestReport report = TestReportParser.parse(log);

            assertThat(report.status()).isEqualTo(TestRunStatus.CRASHED);
            assertThat(report.passed()).isEqualTo(1);
            assertThat(report.total()).isEqualTo(3);
            assertThat(report.tests()).extracting(TestCaseResult::status)
                    .containsExactly(TestCaseStatus.PASSED, TestCaseStatus.CRASHED, TestCaseStatus.NOT_RUN);
        }

        @Test
        void timeoutEvent_marksRunningTestAsTimeout() {
            String log = plan("t1", "t2", "t3") + start("t1") + passed("t1") + start("t2")
                    + P + "{\"event\":\"timeout\"}\n";

            TestReport report = TestReportParser.parse(log);

            assertThat(report.status()).isEqualTo(TestRunStatus.TIMEOUT);
            assertThat(report.tests()).extracting(TestCaseResult::status)
                    .containsExactly(TestCaseStatus.PASSED, TestCaseStatus.TIMEOUT, TestCaseStatus.NOT_RUN);
        }

        @Test
        void gitlabJobTimeout_detectedWithoutEvent() {
            String log = plan("t1") + start("t1")
                    + "ERROR: Job failed: execution took longer than 1h0m0s seconds\n";

            TestReport report = TestReportParser.parse(log);

            assertThat(report.status()).isEqualTo(TestRunStatus.TIMEOUT);
            assertThat(report.tests().getFirst().status()).isEqualTo(TestCaseStatus.TIMEOUT);
        }

        @Test
        void compileFailed_collectsCompilerOutputUntilTrailer() {
            String log = "$ g++ -std=c++17 -DGRADER_MAIN -o test_runner test.cpp 2> compile.log || { # collapsed\n"
                    + P + "{\"event\":\"compile_failed\"}\n"
                    + "solution.cpp:3:5: error: expected ';' before '}' token\n"
                    + "    3 |   }\n"
                    + "Cleaning up project directory and file based variables\n"
                    + "ERROR: Job failed: exit code 1\n";

            TestReport report = TestReportParser.parse(log);

            assertThat(report.status()).isEqualTo(TestRunStatus.COMPILE_ERROR);
            assertThat(report.compileOutput())
                    .isEqualTo("solution.cpp:3:5: error: expected ';' before '}' token\n    3 |   }");
            assertThat(report.total()).isZero();
            assertThat(report.detailsAvailable()).isTrue();
        }

        @Test
        void compileFailed_withInlineOutput_python() {
            String log = P + "{\"event\": \"compile_failed\", \"output\": \"SyntaxError: invalid syntax\"}\n"
                    + plan() + END;

            TestReport report = TestReportParser.parse(log);

            assertThat(report.status()).isEqualTo(TestRunStatus.COMPILE_ERROR);
            assertThat(report.compileOutput()).isEqualTo("SyntaxError: invalid syntax");
        }

        @Test
        void stripsAnsiCodesSectionMarkersAndCarriageReturns() {
            String log = "\u001B[0Ksection_start:1700000000:step_script\r\u001B[0K\u001B[0K\u001B[36;1mExecuting\u001B[0;m\r\n"
                    + plan("t1").replace("\n", "\r\n")
                    + start("t1") + "\u001B[32m" + passed("t1").strip() + "\u001B[0m\r\n"
                    + END
                    + "section_end:1700000001:step_script\r\u001B[0K";

            TestReport report = TestReportParser.parse(log);

            assertThat(report.status()).isEqualTo(TestRunStatus.COMPLETED);
            assertThat(report.passed()).isEqualTo(1);
        }

        @Test
        void eventAfterStudentOutputOnSameLine_andTrailingProgressChars() {
            String log = plan("t1", "t2") + start("t1")
                    + "noise" + passed("t1")
                    + start("t2") + "F" + failed("t2", "1", "2").strip() + "F\n" + END;

            TestReport report = TestReportParser.parse(log);

            assertThat(report.passed()).isEqualTo(1);
            assertThat(report.tests().get(1).status()).isEqualTo(TestCaseStatus.FAILED);
        }

        @Test
        void truncatedLog_keepsParsedPrefix() {
            String log = plan("t1", "t2") + start("t1") + passed("t1") + P + "{\"event\":\"start\",\"na";

            TestReport report = TestReportParser.parse(log);

            assertThat(report.status()).isEqualTo(TestRunStatus.CRASHED);
            assertThat(report.tests()).extracting(TestCaseResult::status)
                    .containsExactly(TestCaseStatus.PASSED, TestCaseStatus.NOT_RUN);
        }

        @Test
        void malformedJson_isIgnored() {
            String log = plan("t1") + P + "{not json\n" + P + "[1,2]\n" + P + "{\"no_event\":1}\n"
                    + start("t1") + passed("t1") + END;

            TestReport report = TestReportParser.parse(log);

            assertThat(report.status()).isEqualTo(TestRunStatus.COMPLETED);
            assertThat(report.total()).isEqualTo(1);
        }

        @Test
        void echoedGitlabCommand_isNotAnEvent() {
            String log = "$ timeout 10s ./test_runner || { rc=$?; echo '" + P + "{\"event\":\"timeout\"}'; }\n"
                    + plan("t1") + start("t1") + passed("t1") + END;

            TestReport report = TestReportParser.parse(log);

            assertThat(report.status()).isEqualTo(TestRunStatus.COMPLETED);
        }
    }

    @Nested
    class AntiCheat {

        @Test
        void fakeResultBeforeGenuineOne_isOverridden() {
            String log = plan("t1") + start("t1") + passed("t1") + failed("t1", "1", "2") + END;

            TestReport report = TestReportParser.parse(log);

            assertThat(report.passed()).isZero();
            assertThat(report.tests().getFirst().status()).isEqualTo(TestCaseStatus.FAILED);
        }

        @Test
        void fakeResultForOtherTest_isRejected() {
            String log = plan("t1", "t2") + start("t1") + passed("t2") + failed("t1", "1", "2")
                    + start("t2") + failed("t2", "3", "4") + END;

            TestReport report = TestReportParser.parse(log);

            assertThat(report.passed()).isZero();
        }

        @Test
        void eventsAfterEnd_areIgnored() {
            String log = plan("t1") + start("t1") + failed("t1", "1", "2") + END + passed("t1");

            TestReport report = TestReportParser.parse(log);

            assertThat(report.passed()).isZero();
        }

        @Test
        void unknownTestName_isRejectedOncePlanExists() {
            String log = plan("t1") + start("extra") + passed("extra") + start("t1") + passed("t1") + END;

            TestReport report = TestReportParser.parse(log);

            assertThat(report.total()).isEqualTo(1);
            assertThat(report.tests()).extracting(TestCaseResult::name).containsExactly("t1");
        }

        @Test
        void duplicatePlan_isIgnored() {
            String log = plan("t1", "t2") + start("t1") + plan("t1") + passed("t1")
                    + start("t2") + passed("t2") + END;

            TestReport report = TestReportParser.parse(log);

            assertThat(report.total()).isEqualTo(2);
        }

        @Test
        void exitBeforeEnd_isReportedAsCrashed() {
            String log = plan("t1") + start("t1") + passed("t1");

            TestReport report = TestReportParser.parse(log);

            assertThat(report.status()).isEqualTo(TestRunStatus.CRASHED);
            assertThat(report.passed()).isEqualTo(1);
        }

        @Test
        void longValues_areTruncated() {
            String big = "x".repeat(5000);
            String log = plan("t1") + start("t1") + failed("t1", big, "y") + END;

            TestReport report = TestReportParser.parse(log);

            assertThat(report.tests().getFirst().expected()).hasSize(TestReportParser.MAX_VALUE_CHARS + 3);
        }
    }

    @Nested
    class Fallback {

        @Test
        void legacyPassFailLines() {
            String log = "PASS test_one\nFAIL test_two: expected 5 got 6\nFAIL test_three: wrong\n";

            TestReport report = TestReportParser.parse(log);

            assertThat(report.fromEvents()).isFalse();
            assertThat(report.detailsAvailable()).isTrue();
            assertThat(report.passed()).isEqualTo(1);
            assertThat(report.total()).isEqualTo(3);
            assertThat(report.tests().get(1).expected()).isEqualTo("5");
            assertThat(report.tests().get(1).actual()).isEqualTo("6");
            assertThat(report.tests().get(2).message()).isEqualTo("wrong");
        }

        @Test
        void pytestSummaryLines() {
            String log = """
                    =========================== short test summary info ============================
                    PASSED test_solution.py::test_ok
                    FAILED test_solution.py::test_bad[1-2] - assert 3 == 4
                    ERROR test_solution.py::test_fixture - RuntimeError: boom
                    """;

            TestReport report = TestReportParser.parse(log);

            assertThat(report.total()).isEqualTo(3);
            assertThat(report.tests()).extracting(TestCaseResult::name)
                    .containsExactly("test_ok", "test_bad[1-2]", "test_fixture");
            assertThat(report.tests().get(1).message()).isEqualTo("assert 3 == 4");
            assertThat(report.tests().get(2).status()).isEqualTo(TestCaseStatus.ERROR);
        }

        @Test
        void assertOnlyLog_hasNoDetails() {
            String log = "test_runner: test.cpp:10: int main(): Assertion `add(1, 2) == 3' failed.\nAborted\n";

            TestReport report = TestReportParser.parse(log);

            assertThat(report.detailsAvailable()).isFalse();
            assertThat(report.status()).isEqualTo(TestRunStatus.UNKNOWN);
        }

        @Test
        void emptyOrNullLog_hasNoDetails() {
            assertThat(TestReportParser.parse(null).detailsAvailable()).isFalse();
            assertThat(TestReportParser.parse("  ").detailsAvailable()).isFalse();
        }
    }
}
