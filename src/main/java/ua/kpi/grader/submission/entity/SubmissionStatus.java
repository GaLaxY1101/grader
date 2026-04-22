package ua.kpi.grader.submission.entity;

public enum SubmissionStatus {
    /** Submitted, waiting for a CI pipeline to pick it up. */
    PENDING,
    /** GitLab pipeline is currently running. */
    RUNNING,
    /** All tests passed; score has been assigned. */
    PASSED,
    /** One or more tests failed; score has been assigned. */
    FAILED,
    /** Pipeline could not complete due to a configuration or infrastructure error. */
    ERROR
}
