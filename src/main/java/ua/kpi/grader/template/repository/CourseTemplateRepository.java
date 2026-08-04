package ua.kpi.grader.template.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ua.kpi.grader.template.entity.CourseTemplate;

public interface CourseTemplateRepository extends JpaRepository<CourseTemplate, Long> {

    /**
     * Returns templates visible to the given teacher: owned by them, or explicitly
     * shared with them. Optional case-insensitive substring match against name.
     */
    @Query("""
            SELECT DISTINCT t FROM CourseTemplate t
            WHERE (t.owner.id = :teacherId
                   OR EXISTS (SELECT 1 FROM TemplateShare s
                              WHERE s.template = t
                                AND s.sharedWithTeacher.id = :teacherId))
              AND (CAST(:query AS string) IS NULL
                   OR LOWER(t.name) LIKE CONCAT('%', CAST(:query AS string), '%'))
            """)
    Page<CourseTemplate> findVisibleTo(@Param("teacherId") Long teacherId,
                                       @Param("query") String query,
                                       Pageable pageable);

    /**
     * Returns a page of all templates (admin-only view). Optional name filter.
     */
    @Query("""
            SELECT t FROM CourseTemplate t
            WHERE (CAST(:query AS string) IS NULL
                   OR LOWER(t.name) LIKE CONCAT('%', CAST(:query AS string), '%'))
            """)
    Page<CourseTemplate> findAllFiltered(@Param("query") String query, Pageable pageable);
}
