package ua.kpi.grader.template.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ua.kpi.grader.template.entity.TemplateAssignment;

import java.util.List;

public interface TemplateAssignmentRepository extends JpaRepository<TemplateAssignment, Long> {

    List<TemplateAssignment> findAllByTemplateId(Long templateId);
}
