package ua.kpi.grader.course.entity;

/**
 * How much of the per-test feedback students see for a programming task.
 * Teachers and admins always see the full report.
 */
public enum FeedbackLevel {
    /** Test names with expected and actual values. */
    FULL,
    /** Pass/fail per test without values (for hidden tests). */
    NAMES_ONLY,
    /** Only the number of passed tests. */
    SUMMARY
}
