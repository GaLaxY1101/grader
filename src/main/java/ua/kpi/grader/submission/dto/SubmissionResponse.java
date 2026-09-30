package ua.kpi.grader.submission.dto;

import ua.kpi.grader.course.entity.AssignmentType;
import ua.kpi.grader.submission.entity.Submission;
import ua.kpi.grader.submission.entity.SubmissionFileState;
import ua.kpi.grader.submission.entity.SubmissionStatus;

import java.time.OffsetDateTime;

public record SubmissionResponse(
        Long id,
        Long assignmentId,
        AssignmentType assignmentType,
        Long studentId,
        String studentEmail,
        SubmissionStatus status,
        Integer score,
        Integer bestScore,
        Integer grade,
        int attemptCount,
        Long latestAttemptId,
        SubmissionFileState fileState,
        String returnComment,
        OffsetDateTime submittedAt,
        OffsetDateTime returnedAt,
        int attachmentCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static SubmissionResponse from(Submission submission) {
        return new SubmissionResponse(
                submission.getId(),
                submission.getAssignment().getId(),
                submission.getAssignment().getType(),
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
                submission.getFileState(),
                submission.getReturnComment(),
                submission.getSubmittedAt(),
                submission.getReturnedAt(),
                submission.getAttachments() != null ? submission.getAttachments().size() : 0,
                submission.getCreatedAt(),
                submission.getUpdatedAt()
        );
    }
}
