package ua.kpi.grader.course.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ua.kpi.grader.course.entity.CourseEnrollment;

import java.util.List;
import java.util.Optional;

public interface CourseEnrollmentRepository extends JpaRepository<CourseEnrollment, Long> {

    boolean existsByCourseIdAndStudentId(Long courseId, Long studentId);

    Optional<CourseEnrollment> findByCourseIdAndStudentId(Long courseId, Long studentId);

    @Query("SELECT ce FROM CourseEnrollment ce JOIN FETCH ce.student s JOIN FETCH s.user WHERE ce.course.id = :courseId")
    List<CourseEnrollment> findAllByCourseIdWithStudentUser(@Param("courseId") Long courseId);
}
