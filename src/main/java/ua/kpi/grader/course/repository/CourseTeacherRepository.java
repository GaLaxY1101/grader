package ua.kpi.grader.course.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ua.kpi.grader.course.entity.CourseTeacher;

import java.util.List;
import java.util.Optional;

public interface CourseTeacherRepository extends JpaRepository<CourseTeacher, Long> {

    boolean existsByCourseIdAndTeacherId(Long courseId, Long teacherId);

    Optional<CourseTeacher> findByCourseIdAndTeacherId(Long courseId, Long teacherId);

    @Query("SELECT ct FROM CourseTeacher ct JOIN FETCH ct.teacher t JOIN FETCH t.user WHERE ct.course.id = :courseId")
    List<CourseTeacher> findAllByCourseIdWithTeacherUser(@Param("courseId") Long courseId);
}
