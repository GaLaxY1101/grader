package ua.kpi.grader.submission.dto;

import ua.kpi.grader.course.entity.FeedbackLevel;
import ua.kpi.grader.submission.entity.Attempt;
import ua.kpi.grader.submission.entity.SubmissionStatus;

public record AttemptStatusResponse(
        Long attemptId,
        Integer attemptNumber,
        SubmissionStatus status,
        Integer score,
        /**
         * Raw CI log. Staff always get it; students only when no structured report exists
         * (legacy test files, custom CI templates) and the task's feedback level is FULL.
         */
        String pipelineOutput,
        TestReportResponse testReport
) {
    /**
     * Maps an attempt, filtering test feedback by the given level.
     *
     * @param level FULL for staff; the task's feedback level for students
     * @param staff whether the caller is a TEACHER or ADMIN
     */
    public static AttemptStatusResponse from(Attempt attempt, FeedbackLevel level, boolean staff) {
        return new AttemptStatusResponse(
                attempt.getId(),
                attempt.getAttemptNumber(),
                attempt.getStatus(),
                attempt.getScore(),
                AttemptResponse.visibleLog(attempt, level, staff),
                TestReportResponse.from(attempt, level)
        );
    }
}
