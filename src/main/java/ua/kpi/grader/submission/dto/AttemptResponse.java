package ua.kpi.grader.submission.dto;

import ua.kpi.grader.course.entity.FeedbackLevel;
import ua.kpi.grader.submission.entity.Attempt;
import ua.kpi.grader.submission.entity.SubmissionStatus;

import java.time.OffsetDateTime;

public record AttemptResponse(
        Long id,
        Long submissionId,
        Integer attemptNumber,
        SubmissionStatus status,
        Integer score,
        String codeContent,
        Long gitlabPipelineId,
        /**
         * Raw CI log. Staff always get it; students only when no structured report exists
         * (legacy test files, custom CI templates) and the task's feedback level is FULL.
         */
        String pipelineOutput,
        TestReportResponse testReport,
        OffsetDateTime submittedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    /**
     * Maps an attempt, filtering test feedback by the given level.
     *
     * @param level FULL for staff; the task's feedback level for students
     * @param staff whether the caller is a TEACHER or ADMIN
     */
    public static AttemptResponse from(Attempt attempt, FeedbackLevel level, boolean staff) {
        return new AttemptResponse(
                attempt.getId(),
                attempt.getSubmission().getId(),
                attempt.getAttemptNumber(),
                attempt.getStatus(),
                attempt.getScore(),
                attempt.getCodeContent(),
                attempt.getGitlabPipelineId(),
                visibleLog(attempt, level, staff),
                TestReportResponse.from(attempt, level),
                attempt.getSubmittedAt(),
                attempt.getCreatedAt(),
                attempt.getUpdatedAt()
        );
    }

    /** Raw log as the caller may see it; see {@link #pipelineOutput()}. */
    static String visibleLog(Attempt attempt, FeedbackLevel level, boolean staff) {
        if (staff) {
            return attempt.getPipelineOutput();
        }
        boolean noStructuredReport = attempt.getTestRunStatus() == null;
        return level == FeedbackLevel.FULL && noStructuredReport ? attempt.getPipelineOutput() : null;
    }
}
