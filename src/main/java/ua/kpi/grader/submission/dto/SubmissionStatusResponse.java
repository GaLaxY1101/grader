package ua.kpi.grader.submission.dto;

import ua.kpi.grader.submission.entity.Submission;
import ua.kpi.grader.submission.entity.SubmissionStatus;

public record SubmissionStatusResponse(
        Long id,
        SubmissionStatus status,
        Integer score,
        Integer bestScore,
        Long latestAttemptId
) {
    public static SubmissionStatusResponse from(Submission submission) {
        return new SubmissionStatusResponse(
                submission.getId(),
                submission.getStatus(),
                submission.getScore(),
                submission.getBestScore(),
                submission.getLatestAttempt() != null
                        ? submission.getLatestAttempt().getId()
                        : null
        );
    }
}
