package ua.kpi.grader.submission.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.kpi.grader.common.exception.ResourceNotFoundException;
import ua.kpi.grader.course.repository.AssignmentRepository;
import ua.kpi.grader.security.CurrentUser;
import ua.kpi.grader.submission.dto.CreateSubmissionRequest;
import ua.kpi.grader.submission.dto.SubmissionResponse;
import ua.kpi.grader.submission.dto.SubmissionStatusResponse;
import ua.kpi.grader.submission.entity.Submission;
import ua.kpi.grader.submission.repository.SubmissionRepository;
import ua.kpi.grader.user.entity.Student;
import ua.kpi.grader.user.repository.StudentRepository;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SubmissionServiceImpl implements SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final AssignmentRepository assignmentRepository;
    private final StudentRepository studentRepository;
    private final CurrentUser currentUser;

    /**
     * Creates a new submission for the given assignment on behalf of the authenticated student.
     */
    @Override
    @Transactional
    public SubmissionResponse createSubmission(Long assignmentId, CreateSubmissionRequest request) {
        var assignment = assignmentRepository.findByIdAndIsActiveTrue(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Assignment not found with id: " + assignmentId));

        String email = currentUser.getEmail();
        Student student = studentRepository.findByUser_Email(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Student not found for user: " + email));

        Submission submission = Submission.builder()
                .assignment(assignment)
                .student(student)
                .codeContent(request.codeContent())
                .build();

        return SubmissionResponse.from(submissionRepository.save(submission));
    }

    /**
     * Returns the full submission by ID.
     * Students may only access their own submissions; teachers/admins may access any.
     */
    @Override
    @Transactional(readOnly = true)
    public SubmissionResponse findById(Long id) {
        Submission submission = findWithDetailsOrThrow(id);
        enforceStudentOwnership(submission);
        return SubmissionResponse.from(submission);
    }

    /**
     * Returns a lightweight status snapshot for polling.
     * Students may only poll their own submissions; teachers/admins may poll any.
     */
    @Override
    @Transactional(readOnly = true)
    public SubmissionStatusResponse getStatus(Long id) {
        Submission submission = findWithDetailsOrThrow(id);
        enforceStudentOwnership(submission);
        return SubmissionStatusResponse.from(submission);
    }

    /**
     * Returns all submissions for an assignment, newest first.
     */
    @Override
    @Transactional(readOnly = true)
    public List<SubmissionResponse> listByAssignment(Long assignmentId) {
        if (!assignmentRepository.existsById(assignmentId)) {
            throw new ResourceNotFoundException("Assignment not found with id: " + assignmentId);
        }
        return submissionRepository.findAllByAssignmentIdOrderBySubmittedAtDesc(assignmentId).stream()
                .map(SubmissionResponse::from)
                .toList();
    }

    /**
     * Returns the authenticated student's latest submission for the given assignment.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<SubmissionResponse> getMyLatestSubmission(Long assignmentId) {
        if (!assignmentRepository.existsById(assignmentId)) {
            throw new ResourceNotFoundException("Assignment not found with id: " + assignmentId);
        }
        String email = currentUser.getEmail();
        Student student = studentRepository.findByUser_Email(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Student not found for user: " + email));
        return submissionRepository
                .findAllByAssignmentIdAndStudentIdOrderBySubmittedAtDesc(assignmentId, student.getId())
                .stream()
                .findFirst()
                .map(SubmissionResponse::from);
    }

    private Submission findWithDetailsOrThrow(Long id) {
        return submissionRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Submission not found with id: " + id));
    }

    /**
     * If the current user is a STUDENT, verifies they own the submission.
     * Teachers and admins bypass this check.
     */
    private void enforceStudentOwnership(Submission submission) {
        if (currentUser.hasRole("STUDENT")) {
            String email = currentUser.getEmail();
            if (!submission.getStudent().getUser().getEmail().equals(email)) {
                throw new AccessDeniedException(
                        "Access denied to submission " + submission.getId());
            }
        }
    }
}
