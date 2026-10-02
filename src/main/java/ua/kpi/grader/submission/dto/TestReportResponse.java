package ua.kpi.grader.submission.dto;

import ua.kpi.grader.course.entity.FeedbackLevel;
import ua.kpi.grader.submission.entity.Attempt;
import ua.kpi.grader.submission.feedback.TestRunStatus;

import java.util.List;

/**
 * Structured test results of an attempt, filtered by the feedback level the caller may see.
 *
 * @param status           overall outcome of the test run; null when details are not available
 * @param passed           number of passed tests; null when details are not available
 * @param total            number of tests; null when details are not available
 * @param compileOutput    compiler / import error output; only with FULL feedback
 * @param detailsAvailable false for attempts whose log could not be parsed (show the raw log instead)
 * @param feedbackLevel    the level the response was filtered with
 * @param tests            per-test results; empty with SUMMARY feedback
 */
public record TestReportResponse(
        TestRunStatus status,
        Integer passed,
        Integer total,
        String compileOutput,
        boolean detailsAvailable,
        FeedbackLevel feedbackLevel,
        List<TestCaseResponse> tests
) {

    /**
     * Builds the report of an attempt as seen with the given feedback level.
     *
     * @param attempt attempt with its test results
     * @param level   FULL for staff; the task's level for students
     * @return the filtered report
     */
    public static TestReportResponse from(Attempt attempt, FeedbackLevel level) {
        if (attempt.getTestRunStatus() == null) {
            return new TestReportResponse(null, null, null, null, false, level, List.of());
        }
        boolean full = level == FeedbackLevel.FULL;
        List<TestCaseResponse> tests = level == FeedbackLevel.SUMMARY
                ? List.of()
                : attempt.getTestResults().stream().map(r -> TestCaseResponse.from(r, full)).toList();
        return new TestReportResponse(
                attempt.getTestRunStatus(),
                attempt.getTestsPassed(),
                attempt.getTestsTotal(),
                full ? attempt.getCompileOutput() : null,
                true,
                level,
                tests
        );
    }
}
