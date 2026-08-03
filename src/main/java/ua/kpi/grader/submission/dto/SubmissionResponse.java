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
        Integer bestScore,
        Integer grade,
        int attemptCount,
        Long latestAttemptId,
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
                submission.getBestScore(),
                submission.getGrade(),
                submission.getAttempts().size(),
                submission.getLatestAttempt() != null
                        ? submission.getLatestAttempt().getId()
                        : null,
                submission.getCreatedAt(),
                submission.getUpdatedAt()
        );
    }
}
