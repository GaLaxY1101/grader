package ua.kpi.grader.submission.feedback;

/** Outcome of a single test case in a graded run. */
public enum TestCaseStatus {
    /** The test passed. */
    PASSED,
    /** An expectation/assertion failed; expected and actual values are recorded when known. */
    FAILED,
    /** The test raised an unexpected exception. */
    ERROR,
    /** The test started but the program terminated before it finished (segfault, abort, exit()). */
    CRASHED,
    /** The run timed out while this test was running. */
    TIMEOUT,
    /** The test was planned but never started because the run ended earlier. */
    NOT_RUN,
    /** The test was skipped by the test framework. */
    SKIPPED
}
