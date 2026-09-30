package ua.kpi.grader.template.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import ua.kpi.grader.common.exception.ResourceNotFoundException;
import ua.kpi.grader.security.CurrentUser;
import ua.kpi.grader.storage.AttachmentValidator;
import ua.kpi.grader.storage.MinioProperties;
import ua.kpi.grader.storage.StorageService;
import ua.kpi.grader.storage.StoredObject;
import ua.kpi.grader.storage.dto.AttachmentSummary;
import ua.kpi.grader.storage.dto.UploadFailure;
import ua.kpi.grader.storage.dto.UploadResponse;
import ua.kpi.grader.template.entity.TemplateAssignment;
import ua.kpi.grader.template.entity.TemplateAssignmentAttachment;
import ua.kpi.grader.template.repository.TemplateAssignmentAttachmentRepository;
import ua.kpi.grader.template.repository.TemplateAssignmentRepository;
import ua.kpi.grader.user.entity.Teacher;
import ua.kpi.grader.user.repository.TeacherRepository;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TemplateAssignmentAttachmentServiceImpl implements TemplateAssignmentAttachmentService {

    private final TemplateAssignmentRepository assignmentRepository;
    private final TemplateAssignmentAttachmentRepository attachmentRepository;
    private final TeacherRepository teacherRepository;
    private final TemplateAccessService access;
    private final CurrentUser currentUser;
    private final StorageService storage;
    private final MinioProperties minioProperties;

    @Override
    @Transactional
    public UploadResponse upload(Long templateAssignmentId, List<MultipartFile> files) {
        AttachmentValidator.ensureBatchLimit(files.size());
        TemplateAssignment assignment = findOrThrow(templateAssignmentId);
        access.requireEdit(assignment.getTemplate().getId());
        Teacher teacher = currentTeacher();

        List<AttachmentSummary> uploaded = new ArrayList<>();
        List<UploadFailure> failed = new ArrayList<>();

        for (MultipartFile file : files) {
            try {
                String filename = AttachmentValidator.validate(file);
                String key = "template-assignments/%d/%s_%s".formatted(
                        templateAssignmentId, UUID.randomUUID(), filename);
                StoredObject stored = storage.put(key, file);
                TemplateAssignmentAttachment saved = persist(assignment, teacher, filename, stored);
                uploaded.add(toSummary(saved));
            } catch (ResponseStatusException e) {
                failed.add(new UploadFailure(safeName(file), e.getReason()));
            }
        }
        return new UploadResponse(uploaded, failed);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttachmentSummary> list(Long templateAssignmentId) {
        TemplateAssignment assignment = findOrThrow(templateAssignmentId);
        access.requireView(assignment.getTemplate().getId());
        return attachmentRepository
                .findAllByTemplateAssignmentIdOrderByCreatedAtDesc(templateAssignmentId).stream()
                .map(TemplateAssignmentAttachmentServiceImpl::toSummary)
                .toList();
    }

    @Override
    @Transactional
    public void delete(Long templateAssignmentId, Long attachmentId) {
        TemplateAssignment assignment = findOrThrow(templateAssignmentId);
        access.requireEdit(assignment.getTemplate().getId());
        TemplateAssignmentAttachment attachment = attachmentRepository
                .findByIdAndTemplateAssignmentId(attachmentId, templateAssignmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Attachment not found with id: " + attachmentId));
        storage.delete(attachment.getStorageKey());
        attachmentRepository.delete(attachment);
    }

    @Override
    @Transactional(readOnly = true)
    public URI download(Long templateAssignmentId, Long attachmentId) {
        TemplateAssignment assignment = findOrThrow(templateAssignmentId);
        access.requireView(assignment.getTemplate().getId());
        TemplateAssignmentAttachment attachment = attachmentRepository
                .findByIdAndTemplateAssignmentId(attachmentId, templateAssignmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Attachment not found with id: " + attachmentId));
        return storage.presignGet(attachment.getStorageKey(),
                Duration.ofSeconds(minioProperties.presignTtlSeconds()));
    }

    private TemplateAssignmentAttachment persist(TemplateAssignment assignment, Teacher teacher,
                                                 String filename, StoredObject stored) {
        TemplateAssignmentAttachment entity = TemplateAssignmentAttachment.builder()
                .templateAssignment(assignment)
                .filename(filename)
                .contentType(stored.contentType())
                .sizeBytes(stored.size())
                .storageKey(stored.key())
                .uploadedBy(teacher)
                .build();
        TemplateAssignmentAttachment saved = attachmentRepository.save(entity);
        assignment.addAttachment(saved);
        return saved;
    }

    private TemplateAssignment findOrThrow(Long id) {
        return assignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Template assignment not found with id: " + id));
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

    private static AttachmentSummary toSummary(TemplateAssignmentAttachment a) {
        return new AttachmentSummary(
                a.getId(),
                a.getFilename(),
                a.getContentType(),
                a.getSizeBytes(),
                a.getCreatedAt(),
                a.getUploadedBy() != null ? a.getUploadedBy().getId() : null);
    }
}
