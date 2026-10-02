package ua.kpi.grader.submission.feedback;

import java.util.List;

/**
 * Structured test results extracted from a job log.
 *
 * @param status           overall outcome of the run
 * @param compileOutput    compiler / import error output when {@code status} is COMPILE_ERROR, else null
 * @param passed           number of passed tests
 * @param total            number of tests (planned or observed)
 * @param tests            per-test results in run order
 * @param detailsAvailable false when nothing could be parsed and only the raw log is meaningful
 * @param fromEvents       true when the results come from {@code ##GRADER##} harness events
 *                         (as opposed to legacy PASS/FAIL or pytest summary lines)
 */
public record TestReport(
        TestRunStatus status,
        String compileOutput,
        int passed,
        int total,
        List<TestCaseResult> tests,
        boolean detailsAvailable,
        boolean fromEvents
) {

    /** A report for a log without any recognizable test output. */
    public static TestReport unavailable() {
        return new TestReport(TestRunStatus.UNKNOWN, null, 0, 0, List.of(), false, false);
    }

    /** True when at least one test did not pass. */
    public boolean hasNonPassingTests() {
        return passed < total;
    }
}
