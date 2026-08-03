package ua.kpi.grader.course.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ua.kpi.grader.course.entity.Course;

public interface CourseRepository extends JpaRepository<Course, Long> {

    /**
     * Searches courses filtered by active status, a free-text term matched against
     * either the course name or the code of any academic group whose members are
     * currently ACTIVE-enrolled in the course, and optionally a specific group id.
     *
     * @param query    normalized search term (already trimmed & lower-cased,
     *                 or {@code null} to disable text filtering)
     * @param groupId  restrict to courses that have at least one ACTIVE enrollment
     *                 from a member of this group ({@code null} = no restriction)
     * @param isActive course active flag
     * @param pageable paging & sort
     */
    @Query("""
            SELECT c FROM Course c
            WHERE c.isActive = :isActive
              AND (CAST(:query AS string) IS NULL
                   OR LOWER(c.name) LIKE CONCAT('%', CAST(:query AS string), '%')
                   OR EXISTS (SELECT 1 FROM CourseEnrollment ce
                                JOIN GroupStudent gs ON gs.student = ce.student
                               WHERE ce.course = c
                                 AND ce.status = 'ACTIVE'
                                 AND gs.graduatedAt IS NULL
                                 AND LOWER(gs.group.code) LIKE CONCAT('%', CAST(:query AS string), '%')))
              AND (:groupId IS NULL
                   OR EXISTS (SELECT 1 FROM CourseEnrollment ce2
                                JOIN GroupStudent gs2 ON gs2.student = ce2.student
                               WHERE ce2.course = c
                                 AND ce2.status = 'ACTIVE'
                                 AND gs2.graduatedAt IS NULL
                                 AND gs2.group.id = :groupId))
            """)
    Page<Course> search(@Param("query") String query,
                        @Param("groupId") Long groupId,
                        @Param("isActive") boolean isActive,
                        Pageable pageable);
}
