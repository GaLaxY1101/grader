package ua.kpi.grader.submission.dto;

import ua.kpi.grader.submission.entity.Attempt;
import ua.kpi.grader.submission.entity.SubmissionStatus;

public record AttemptStatusResponse(
        Long attemptId,
        Integer attemptNumber,
        SubmissionStatus status,
        Integer score,
        String pipelineOutput
) {
    public static AttemptStatusResponse from(Attempt attempt) {
        return new AttemptStatusResponse(
                attempt.getId(),
                attempt.getAttemptNumber(),
                attempt.getStatus(),
                attempt.getScore(),
                attempt.getPipelineOutput()
        );
    }
}
