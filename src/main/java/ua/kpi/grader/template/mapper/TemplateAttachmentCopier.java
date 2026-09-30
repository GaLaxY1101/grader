package ua.kpi.grader.template.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ua.kpi.grader.course.entity.Assignment;
import ua.kpi.grader.course.entity.AssignmentAttachment;
import ua.kpi.grader.course.repository.AssignmentAttachmentRepository;
import ua.kpi.grader.storage.StorageService;
import ua.kpi.grader.template.entity.TemplateAssignment;
import ua.kpi.grader.template.entity.TemplateAssignmentAttachment;
import ua.kpi.grader.user.entity.Teacher;

import java.util.UUID;

/**
 * Copies attachments from a template assignment onto its freshly-instantiated
 * course Assignment. Each blob is server-side copied inside MinIO and a new
 * AssignmentAttachment row is created, owned by the teacher creating the course.
 */
@Component
@RequiredArgsConstructor
public class TemplateAttachmentCopier {

    private final StorageService storage;
    private final AssignmentAttachmentRepository attachmentRepository;

    public void copyAll(TemplateAssignment source, Assignment target, Teacher owner) {
        if (source.getAttachments() == null || source.getAttachments().isEmpty()) {
            return;
        }
        for (TemplateAssignmentAttachment src : source.getAttachments()) {
            String destKey = "assignments/%d/%s_%s".formatted(
                    target.getId(), UUID.randomUUID(), src.getFilename());
            storage.copy(src.getStorageKey(), destKey);

            AssignmentAttachment copy = AssignmentAttachment.builder()
                    .assignment(target)
                    .filename(src.getFilename())
                    .contentType(src.getContentType())
                    .sizeBytes(src.getSizeBytes())
                    .storageKey(destKey)
                    .uploadedBy(owner)
                    .build();
            AssignmentAttachment saved = attachmentRepository.save(copy);
            target.addAttachment(saved);
        }
    }
}
