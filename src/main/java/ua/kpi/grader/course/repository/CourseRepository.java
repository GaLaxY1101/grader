package ua.kpi.grader.course.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ua.kpi.grader.course.entity.Course;

import java.util.List;

public interface CourseRepository extends JpaRepository<Course, Long> {

    List<Course> findAllByIsActiveTrue();
}
