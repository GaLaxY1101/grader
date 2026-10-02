package ua.kpi.grader.submission.feedback;

/**
 * Result of a single test case parsed from a job log.
 *
 * @param name       test name
 * @param status     outcome
 * @param expected   expected value rendered as a source-like literal, or null
 * @param actual     actual value rendered as a source-like literal, or null
 * @param message    failure or error message, or null
 * @param durationMs duration in milliseconds, or null if unknown
 */
public record TestCaseResult(
        String name,
        TestCaseStatus status,
        String expected,
        String actual,
        String message,
        Long durationMs
) {
}
