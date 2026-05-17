package ua.kpi.grader.submission.service;

import ua.kpi.grader.submission.dto.*;

import java.util.List;
import java.util.Optional;

public interface SubmissionService {

    /**
     * Creates a new attempt for the given assignment on behalf of the authenticated student.
     * If no submission exists yet for this student-assignment pair, one is created first.
     *
     * @param assignmentId the assignment ID
     * @param request      the submission payload containing the student's code
     * @return the persisted AttemptResponse DTO
     */
    AttemptResponse createSubmission(Long assignmentId, CreateSubmissionRequest request);

    /**
     * Returns the full submission by ID.
     * Students may only access their own submissions; teachers/admins may access any.
     *
     * @param id the submission ID
     * @return the SubmissionResponse DTO
     */
    SubmissionResponse findById(Long id);

    /**
     * Returns a lightweight status snapshot for polling a submission.
     * Students may only poll their own submissions; teachers/admins may poll any.
     *
     * @param id the submission ID
     * @return the SubmissionStatusResponse DTO
     */
    SubmissionStatusResponse getStatus(Long id);

    /**
     * Returns all submissions for an assignment, newest first.
     * Intended for teacher/admin use.
     *
     * @param assignmentId the assignment ID
     * @return list of SubmissionResponse DTOs
     */
    List<SubmissionResponse> listByAssignment(Long assignmentId);

    /**
     * Returns the authenticated student's submission for the given assignment,
     * or empty if the student has not submitted yet.
     *
     * @param assignmentId the assignment ID
     * @return optional SubmissionResponse DTO
     */
    Optional<SubmissionResponse> getMySubmission(Long assignmentId);

    /**
     * Returns all attempts for a submission, newest first.
     * Students may only access their own; teachers/admins may access any.
     *
     * @param submissionId the submission ID
     * @return list of AttemptResponse DTOs
     */
    List<AttemptResponse> listAttempts(Long submissionId);

    /**
     * Returns a lightweight status snapshot for polling a specific attempt.
     * Students may only poll their own attempts; teachers/admins may poll any.
     *
     * @param attemptId the attempt ID
     * @return the AttemptStatusResponse DTO
     */
    AttemptStatusResponse getAttemptStatus(Long attemptId);

    /**
     * Applies the GitLab pipeline result to the matching attempt and its parent submission.
     * Called by the webhook endpoint when GitLab reports a pipeline completion.
     *
     * @param gitlabProjectId  the GitLab project id from the webhook payload
     * @param gitlabPipelineId the GitLab pipeline id from the webhook payload
     * @param gitlabStatus     the pipeline status string from GitLab (e.g. "success", "failed")
     */
    void applyGitLabResult(Long gitlabProjectId, Long gitlabPipelineId, String gitlabStatus);
}
