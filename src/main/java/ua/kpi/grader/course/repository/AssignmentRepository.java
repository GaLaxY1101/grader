package ua.kpi.grader.course.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ua.kpi.grader.course.entity.Assignment;

import java.util.List;
import java.util.Optional;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    @Query("SELECT a FROM Assignment a LEFT JOIN FETCH a.programmingTask WHERE a.course.id = :courseId AND a.isActive = true")
    List<Assignment> findAllByCourseIdAndIsActiveTrue(@Param("courseId") Long courseId);

    @Query("SELECT a FROM Assignment a LEFT JOIN FETCH a.programmingTask WHERE a.id = :id AND a.isActive = true")
    Optional<Assignment> findByIdAndIsActiveTrue(@Param("id") Long id);
}
