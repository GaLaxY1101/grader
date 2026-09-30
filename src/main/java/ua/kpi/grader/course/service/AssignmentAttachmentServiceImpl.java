package ua.kpi.grader.course.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import ua.kpi.grader.common.exception.ResourceNotFoundException;
import ua.kpi.grader.course.entity.Assignment;
import ua.kpi.grader.course.entity.AssignmentAttachment;
import ua.kpi.grader.course.repository.AssignmentAttachmentRepository;
import ua.kpi.grader.course.repository.AssignmentRepository;
import ua.kpi.grader.security.CurrentUser;
import ua.kpi.grader.storage.AttachmentValidator;
import ua.kpi.grader.storage.MinioProperties;
import ua.kpi.grader.storage.StorageService;
import ua.kpi.grader.storage.StoredObject;
import ua.kpi.grader.storage.dto.AttachmentSummary;
import ua.kpi.grader.storage.dto.UploadFailure;
import ua.kpi.grader.storage.dto.UploadResponse;
import ua.kpi.grader.user.entity.Teacher;
import ua.kpi.grader.user.repository.TeacherRepository;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AssignmentAttachmentServiceImpl implements AssignmentAttachmentService {

    private final AssignmentRepository assignmentRepository;
    private final AssignmentAttachmentRepository attachmentRepository;
    private final TeacherRepository teacherRepository;
    private final CurrentUser currentUser;
    private final StorageService storage;
    private final MinioProperties minioProperties;

    @Override
    @Transactional
    public UploadResponse upload(Long assignmentId, List<MultipartFile> files) {
        AttachmentValidator.ensureBatchLimit(files.size());
        Assignment assignment = findAssignmentOrThrow(assignmentId);
        Teacher teacher = currentTeacher();

        List<AttachmentSummary> uploaded = new ArrayList<>();
        List<UploadFailure> failed = new ArrayList<>();

        for (MultipartFile file : files) {
            try {
                String filename = AttachmentValidator.validate(file);
                String key = "assignments/%d/%s_%s".formatted(
                        assignmentId, UUID.randomUUID(), filename);
                StoredObject stored = storage.put(key, file);
                AssignmentAttachment saved = persist(assignment, teacher, filename, stored);
                uploaded.add(toSummary(saved));
            } catch (ResponseStatusException e) {
                failed.add(new UploadFailure(safeName(file), e.getReason()));
            }
        }
        return new UploadResponse(uploaded, failed);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttachmentSummary> list(Long assignmentId) {
        if (!assignmentRepository.existsById(assignmentId)) {
            throw new ResourceNotFoundException("Assignment not found with id: " + assignmentId);
        }
        return attachmentRepository.findAllByAssignmentIdOrderByCreatedAtDesc(assignmentId).stream()
                .map(AssignmentAttachmentServiceImpl::toSummary)
                .toList();
    }

    @Override
    @Transactional
    public void delete(Long assignmentId, Long attachmentId) {
        AssignmentAttachment attachment = attachmentRepository
                .findByIdAndAssignmentId(attachmentId, assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Attachment not found with id: " + attachmentId));
        storage.delete(attachment.getStorageKey());
        attachmentRepository.delete(attachment);
    }

    @Override
    @Transactional(readOnly = true)
    public URI download(Long assignmentId, Long attachmentId) {
        AssignmentAttachment attachment = attachmentRepository
                .findByIdAndAssignmentId(attachmentId, assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Attachment not found with id: " + attachmentId));
        return storage.presignGet(attachment.getStorageKey(),
                Duration.ofSeconds(minioProperties.presignTtlSeconds()));
    }

    private AssignmentAttachment persist(Assignment assignment, Teacher teacher,
                                         String filename, StoredObject stored) {
        AssignmentAttachment entity = AssignmentAttachment.builder()
                .assignment(assignment)
                .filename(filename)
                .contentType(stored.contentType())
                .sizeBytes(stored.size())
                .storageKey(stored.key())
                .uploadedBy(teacher)
                .build();
        AssignmentAttachment saved = attachmentRepository.save(entity);
        assignment.addAttachment(saved);
        return saved;
    }

    private Assignment findAssignmentOrThrow(Long assignmentId) {
        return assignmentRepository.findByIdAndIsActiveTrue(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Assignment not found with id: " + assignmentId));
    }

    private Teacher currentTeacher() {
        String email = currentUser.getEmail();
        return teacherRepository.findByUser_Email(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Teacher not found for user: " + email));
    }

    private static String safeName(MultipartFile file) {
        String original = file.getOriginalFilename();
        return original == null || original.isBlank() ? "unnamed" : original;
    }

    private static AttachmentSummary toSummary(AssignmentAttachment a) {
        return new AttachmentSummary(
                a.getId(),
                a.getFilename(),
                a.getContentType(),
                a.getSizeBytes(),
                a.getCreatedAt(),
                a.getUploadedBy() != null ? a.getUploadedBy().getId() : null);
    }
}
