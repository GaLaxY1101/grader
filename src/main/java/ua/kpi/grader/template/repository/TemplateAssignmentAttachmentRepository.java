package ua.kpi.grader.template.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ua.kpi.grader.template.entity.TemplateAssignmentAttachment;

import java.util.List;
import java.util.Optional;

public interface TemplateAssignmentAttachmentRepository
        extends JpaRepository<TemplateAssignmentAttachment, Long> {

    List<TemplateAssignmentAttachment> findAllByTemplateAssignmentIdOrderByCreatedAtDesc(
            Long templateAssignmentId);

    Optional<TemplateAssignmentAttachment> findByIdAndTemplateAssignmentId(
            Long id, Long templateAssignmentId);
}
