package ua.kpi.grader.submission.feedback;

/** Overall outcome of a graded test run, derived from the job log. */
public enum TestRunStatus {
    /** Every test that was planned produced a result. */
    COMPLETED,
    /** The solution or the tests did not compile / import. */
    COMPILE_ERROR,
    /** The program terminated in the middle of a test. */
    CRASHED,
    /** The run was killed after the time limit. */
    TIMEOUT,
    /** No per-test results could be extracted from the log. */
    UNKNOWN
}
