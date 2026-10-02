package ua.kpi.grader.submission.dto;

import ua.kpi.grader.submission.entity.AttemptTestResult;
import ua.kpi.grader.submission.feedback.TestCaseStatus;

/**
 * One test case of an attempt. {@code expected}, {@code actual} and {@code message} are null
 * when hidden by the task's feedback level.
 */
public record TestCaseResponse(
        String name,
        TestCaseStatus status,
        String expected,
        String actual,
        String message,
        Long durationMs
) {
    static TestCaseResponse from(AttemptTestResult result, boolean includeValues) {
        return new TestCaseResponse(
                result.getName(),
                result.getStatus(),
                includeValues ? result.getExpected() : null,
                includeValues ? result.getActual() : null,
                includeValues ? result.getMessage() : null,
                result.getDurationMs()
        );
    }
}
