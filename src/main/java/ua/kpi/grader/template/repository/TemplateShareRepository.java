package ua.kpi.grader.template.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ua.kpi.grader.template.entity.TemplateShare;

import java.util.List;
import java.util.Optional;

public interface TemplateShareRepository extends JpaRepository<TemplateShare, Long> {

    boolean existsByTemplateIdAndSharedWithTeacherId(Long templateId, Long teacherId);

    Optional<TemplateShare> findByTemplateIdAndSharedWithTeacherId(Long templateId, Long teacherId);

    @Query("""
            SELECT s FROM TemplateShare s
            JOIN FETCH s.sharedWithTeacher t
            JOIN FETCH t.user
            WHERE s.template.id = :templateId
            """)
    List<TemplateShare> findAllByTemplateIdWithTeacherUser(@Param("templateId") Long templateId);
}
