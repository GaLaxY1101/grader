package ua.kpi.grader.submission.dto;

import ua.kpi.grader.submission.entity.Submission;
import ua.kpi.grader.submission.entity.SubmissionStatus;

import java.time.OffsetDateTime;

public record SubmissionResponse(
        Long id,
        Long assignmentId,
        Long studentId,
        String studentEmail,
        SubmissionStatus status,
        Integer score,
        String codeContent,
        Long gitlabPipelineId,
        String pipelineOutput,
        OffsetDateTime submittedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static SubmissionResponse from(Submission submission) {
        return new SubmissionResponse(
                submission.getId(),
                submission.getAssignment().getId(),
                submission.getStudent().getId(),
                submission.getStudent().getUser().getEmail(),
                submission.getStatus(),
                submission.getScore(),
                submission.getCodeContent(),
                submission.getGitlabPipelineId(),
                submission.getPipelineOutput(),
                submission.getSubmittedAt(),
                submission.getCreatedAt(),
                submission.getUpdatedAt()
        );
    }
}
