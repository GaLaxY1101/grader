package ua.kpi.grader.course.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ua.kpi.grader.course.entity.AssignmentAttachment;

import java.util.List;
import java.util.Optional;

public interface AssignmentAttachmentRepository extends JpaRepository<AssignmentAttachment, Long> {

    List<AssignmentAttachment> findAllByAssignmentIdOrderByCreatedAtDesc(Long assignmentId);

    Optional<AssignmentAttachment> findByIdAndAssignmentId(Long id, Long assignmentId);
}
