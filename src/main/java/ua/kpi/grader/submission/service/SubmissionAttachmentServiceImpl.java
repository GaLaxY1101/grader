package ua.kpi.grader.submission.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import ua.kpi.grader.common.exception.ResourceNotFoundException;
import ua.kpi.grader.course.entity.Assignment;
import ua.kpi.grader.course.repository.AssignmentRepository;
import ua.kpi.grader.security.CurrentUser;
import ua.kpi.grader.storage.AttachmentValidator;
import ua.kpi.grader.storage.MinioProperties;
import ua.kpi.grader.storage.StorageService;
import ua.kpi.grader.storage.StoredObject;
import ua.kpi.grader.storage.dto.AttachmentSummary;
import ua.kpi.grader.storage.dto.UploadFailure;
import ua.kpi.grader.storage.dto.UploadResponse;
import ua.kpi.grader.submission.dto.ReturnSubmissionRequest;
import ua.kpi.grader.submission.dto.SubmissionResponse;
import ua.kpi.grader.submission.entity.Submission;
import ua.kpi.grader.submission.entity.SubmissionAttachment;
import ua.kpi.grader.submission.entity.SubmissionFileState;
import ua.kpi.grader.submission.repository.SubmissionAttachmentRepository;
import ua.kpi.grader.submission.repository.SubmissionRepository;
import ua.kpi.grader.user.entity.Student;
import ua.kpi.grader.user.repository.StudentRepository;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubmissionAttachmentServiceImpl implements SubmissionAttachmentService {

    private final SubmissionRepository submissionRepository;
    private final SubmissionAttachmentRepository attachmentRepository;
    private final AssignmentRepository assignmentRepository;
    private final StudentRepository studentRepository;
    private final SubmissionAccess submissionAccess;
    private final CurrentUser currentUser;
    private final StorageService storage;
    private final MinioProperties minioProperties;

    @Override
    @Transactional
    public SubmissionUploadResult upload(Long assignmentId, List<MultipartFile> files) {
        AttachmentValidator.ensureBatchLimit(files.size());
        Assignment assignment = assignmentRepository.findByIdAndIsActiveTrue(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Assignment not found with id: " + assignmentId));
        Student student = currentStudent();
        Submission submission = submissionAccess.getOrCreate(assignment, student);
        submission.initFileStateIfNeeded();

        if (!submission.getFileState().isEditable()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Submission is " + submission.getFileState() + "; upload not allowed");
        }

        long existing = attachmentRepository.countBySubmissionId(submission.getId());
        if (existing + files.size() > AttachmentValidator.MAX_FILES_PER_REQUEST) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Submission would exceed " + AttachmentValidator.MAX_FILES_PER_REQUEST + " attachments");
        }

        List<AttachmentSummary> uploaded = new ArrayList<>();
        List<UploadFailure> failed = new ArrayList<>();

        for (MultipartFile file : files) {
            try {
                String filename = AttachmentValidator.validate(file);
                String key = "submissions/%d/%s_%s".formatted(
                        submission.getId(), UUID.randomUUID(), filename);
                StoredObject stored = storage.put(key, file);
                SubmissionAttachment saved = persist(submission, filename, stored);
                uploaded.add(toSummary(saved));
            } catch (ResponseStatusException e) {
                failed.add(new UploadFailure(safeName(file), e.getReason()));
            }
        }
        return new SubmissionUploadResult(submission.getId(), new UploadResponse(uploaded, failed));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttachmentSummary> list(Long submissionId) {
        Submission submission = loadOrThrow(submissionId);
        enforceReadAccess(submission);
        return attachmentRepository.findAllBySubmissionIdOrderByUploadedAtDesc(submissionId).stream()
                .map(SubmissionAttachmentServiceImpl::toSummary)
                .toList();
    }

    @Override
    @Transactional
    public void delete(Long submissionId, Long attachmentId) {
        Submission submission = loadOrThrow(submissionId);
        enforceStudentOwner(submission);
        if (submission.getFileState() == null || !submission.getFileState().isEditable()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Attachments locked in state " + submission.getFileState());
        }
        SubmissionAttachment attachment = attachmentRepository
                .findByIdAndSubmissionId(attachmentId, submissionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Attachment not found with id: " + attachmentId));
        storage.delete(attachment.getStorageKey());
        submission.removeAttachment(attachment);
        attachmentRepository.delete(attachment);
    }

    @Override
    @Transactional(readOnly = true)
    public URI download(Long submissionId, Long attachmentId) {
        Submission submission = loadOrThrow(submissionId);
        enforceReadAccess(submission);
        SubmissionAttachment attachment = attachmentRepository
                .findByIdAndSubmissionId(attachmentId, submissionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Attachment not found with id: " + attachmentId));
        return storage.presignGet(attachment.getStorageKey(),
                Duration.ofSeconds(minioProperties.presignTtlSeconds()));
    }

    @Override
    @Transactional
    public SubmissionResponse turnIn(Long submissionId) {
        Submission submission = loadOrThrow(submissionId);
        enforceStudentOwner(submission);
        long count = attachmentRepository.countBySubmissionId(submissionId);
        if (count == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "At least one attachment is required before turning in");
        }
        try {
            submission.turnIn();
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
        return SubmissionResponse.from(submission);
    }

    @Override
    @Transactional
    public SubmissionResponse returnSubmission(Long submissionId, ReturnSubmissionRequest request) {
        Submission submission = loadOrThrow(submissionId);
        try {
            submission.markReturned(request != null ? request.comment() : null);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
        return SubmissionResponse.from(submission);
    }

    private SubmissionAttachment persist(Submission submission, String filename, StoredObject stored) {
        SubmissionAttachment entity = SubmissionAttachment.builder()
                .submission(submission)
                .filename(filename)
                .contentType(stored.contentType())
                .sizeBytes(stored.size())
                .storageKey(stored.key())
                .build();
        SubmissionAttachment saved = attachmentRepository.save(entity);
        submission.addAttachment(saved);
        return saved;
    }

    private Submission loadOrThrow(Long submissionId) {
        return submissionRepository.findByIdWithDetails(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Submission not found with id: " + submissionId));
    }

    private Student currentStudent() {
        String email = currentUser.getEmail();
        return studentRepository.findByUser_Email(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Student not found for user: " + email));
    }

    private void enforceStudentOwner(Submission submission) {
        String email = currentUser.getEmail();
        if (!submission.getStudent().getUser().getEmail().equals(email)) {
            throw new AccessDeniedException("Access denied to submission " + submission.getId());
        }
    }

    private void enforceReadAccess(Submission submission) {
        if (currentUser.hasRole("STUDENT")) {
            enforceStudentOwner(submission);
        }
    }

    private static String safeName(MultipartFile file) {
        String original = file.getOriginalFilename();
        return original == null || original.isBlank() ? "unnamed" : original;
    }

    private static AttachmentSummary toSummary(SubmissionAttachment a) {
        return new AttachmentSummary(
                a.getId(),
                a.getFilename(),
                a.getContentType(),
                a.getSizeBytes(),
                a.getUploadedAt(),
                null);
    }
}
