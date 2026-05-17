package ua.kpi.grader.submission.dto;

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
        String pipelineOutput,
        OffsetDateTime submittedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static AttemptResponse from(Attempt attempt) {
        return new AttemptResponse(
                attempt.getId(),
                attempt.getSubmission().getId(),
                attempt.getAttemptNumber(),
                attempt.getStatus(),
                attempt.getScore(),
                attempt.getCodeContent(),
                attempt.getGitlabPipelineId(),
                attempt.getPipelineOutput(),
                attempt.getSubmittedAt(),
                attempt.getCreatedAt(),
                attempt.getUpdatedAt()
        );
    }
}
