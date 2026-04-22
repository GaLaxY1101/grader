package ua.kpi.grader.submission.service;

import ua.kpi.grader.submission.dto.CreateSubmissionRequest;
import ua.kpi.grader.submission.dto.SubmissionResponse;
import ua.kpi.grader.submission.dto.SubmissionStatusResponse;

import java.util.List;
import java.util.Optional;

public interface SubmissionService {

    /**
     * Creates a new submission for the given assignment on behalf of the authenticated student.
     *
     * @param assignmentId the assignment ID
     * @param request      the submission payload
     * @return the persisted SubmissionResponse DTO
     */
    SubmissionResponse createSubmission(Long assignmentId, CreateSubmissionRequest request);

    /**
     * Returns the full submission by ID.
     * Students may only access their own submissions; teachers/admins may access any.
     *
     * @param id the submission ID
     * @return the SubmissionResponse DTO
     */
    SubmissionResponse findById(Long id);

    /**
     * Returns a lightweight status snapshot for polling.
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
     * Returns the authenticated student's latest submission for the given assignment,
     * or empty if the student has not submitted yet.
     *
     * @param assignmentId the assignment ID
     * @return optional SubmissionResponse DTO
     */
    Optional<SubmissionResponse> getMyLatestSubmission(Long assignmentId);
}
