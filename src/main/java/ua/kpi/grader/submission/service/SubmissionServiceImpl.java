package ua.kpi.grader.submission.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.kpi.grader.common.exception.ResourceNotFoundException;
import ua.kpi.grader.course.repository.AssignmentRepository;
import ua.kpi.grader.gitlab.client.GitLabApiClient;
import ua.kpi.grader.gitlab.client.dto.GitLabJobDto;
import ua.kpi.grader.gitlab.service.GitLabSubmissionService;
import ua.kpi.grader.security.CurrentUser;
import ua.kpi.grader.submission.dto.*;
import ua.kpi.grader.submission.entity.Attempt;
import ua.kpi.grader.submission.entity.Submission;
import ua.kpi.grader.submission.entity.SubmissionStatus;
import ua.kpi.grader.submission.repository.AttemptRepository;
import ua.kpi.grader.submission.repository.SubmissionRepository;
import ua.kpi.grader.user.entity.Student;
import ua.kpi.grader.user.repository.StudentRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionServiceImpl implements SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final AttemptRepository attemptRepository;
    private final AssignmentRepository assignmentRepository;
    private final StudentRepository studentRepository;
    private final CurrentUser currentUser;
    private final GitLabSubmissionService gitLabSubmissionService;
    private final GitLabApiClient gitLabApiClient;

    /**
     * Creates a new attempt for the given assignment on behalf of the authenticated student.
     * If no submission exists yet, one is created first (get-or-create pattern).
     */
    @Override
    @Transactional
    public AttemptResponse createSubmission(Long assignmentId, CreateSubmissionRequest request) {
        var assignment = assignmentRepository.findByIdAndIsActiveTrue(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Assignment not found with id: " + assignmentId));

        String email = currentUser.getEmail();
        Student student = studentRepository.findByUser_Email(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Student not found for user: " + email));

        Submission submission = submissionRepository
                .findByAssignmentIdAndStudentId(assignmentId, student.getId())
                .orElseGet(() -> {
                    Submission newSub = Submission.builder()
                            .assignment(assignment)
                            .student(student)
                            .build();
                    return submissionRepository.save(newSub);
                });

        int nextNumber = attemptRepository.findMaxAttemptNumber(submission.getId()) + 1;
        Attempt attempt = Attempt.builder()
                .submission(submission)
                .attemptNumber(nextNumber)
                .codeContent(request.codeContent())
                .build();
        attempt = attemptRepository.save(attempt);

        gitLabSubmissionService.triggerPipeline(submission, attempt);
        submission.updateFromAttempt(attempt);

        return AttemptResponse.from(attempt);
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
        return submissionRepository.findAllByAssignmentIdOrderByUpdatedAtDesc(assignmentId).stream()
                .map(SubmissionResponse::from)
                .toList();
    }

    /**
     * Returns the authenticated student's submission for the given assignment.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<SubmissionResponse> getMySubmission(Long assignmentId) {
        if (!assignmentRepository.existsById(assignmentId)) {
            throw new ResourceNotFoundException("Assignment not found with id: " + assignmentId);
        }
        String email = currentUser.getEmail();
        Student student = studentRepository.findByUser_Email(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Student not found for user: " + email));
        return submissionRepository
                .findByAssignmentIdAndStudentId(assignmentId, student.getId())
                .map(SubmissionResponse::from);
    }

    /**
     * Returns all attempts for a submission, newest first.
     */
    @Override
    @Transactional(readOnly = true)
    public List<AttemptResponse> listAttempts(Long submissionId) {
        Submission submission = findWithDetailsOrThrow(submissionId);
        enforceStudentOwnership(submission);
        return attemptRepository.findAllBySubmissionIdOrderByAttemptNumberDesc(submissionId).stream()
                .map(AttemptResponse::from)
                .toList();
    }

    /**
     * Returns a lightweight status snapshot for polling a specific attempt.
     */
    @Override
    @Transactional(readOnly = true)
    public AttemptStatusResponse getAttemptStatus(Long attemptId) {
        Attempt attempt = attemptRepository.findByIdWithDetails(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Attempt not found with id: " + attemptId));
        enforceStudentOwnership(attempt.getSubmission());
        return AttemptStatusResponse.from(attempt);
    }

    /**
     * Applies the GitLab pipeline result to the matching attempt and its parent submission.
     * Fetches job logs, maps GitLab status to SubmissionStatus, and persists the result.
     */
    @Override
    @Transactional
    public void applyGitLabResult(Long gitlabProjectId, Long gitlabPipelineId, String gitlabStatus) {
        Attempt attempt = attemptRepository.findByGitlabPipelineId(gitlabPipelineId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Attempt not found for pipeline=%d".formatted(gitlabPipelineId)));

        Submission submission = attempt.getSubmission();

        SubmissionStatus status = mapStatus(gitlabStatus);
        Integer score = switch (status) {
            case PASSED -> submission.getAssignment().getMaxScore();
            case FAILED -> 0;
            default -> null;
        };

        String logs = fetchCombinedLogs(gitlabProjectId.intValue(), gitlabPipelineId.intValue());
        attempt.applyResult(status, score, logs);
        submission.updateFromAttempt(attempt);

        log.info("Applied GitLab result to attempt id={} (submission={}): status={}, score={}",
                attempt.getId(), submission.getId(), status, score);
    }

    private SubmissionStatus mapStatus(String gitlabStatus) {
        return switch (gitlabStatus) {
            case "success" -> SubmissionStatus.PASSED;
            case "failed" -> SubmissionStatus.FAILED;
            default -> SubmissionStatus.ERROR;
        };
    }

    private String fetchCombinedLogs(Integer projectId, Integer pipelineId) {
        try {
            List<GitLabJobDto> jobs = gitLabApiClient.getPipelineJobs(projectId, pipelineId);
            return jobs.stream()
                    .map(job -> {
                        String jobLog = gitLabApiClient.getJobLog(projectId, job.id());
                        return "=== Job: %s ===\n%s".formatted(job.name(), jobLog);
                    })
                    .collect(Collectors.joining("\n\n"));
        } catch (org.springframework.web.client.RestClientException e) {
            log.warn("Could not fetch job logs for project={} pipeline={}: {}",
                    projectId, pipelineId, e.getMessage());
            return "";
        }
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
