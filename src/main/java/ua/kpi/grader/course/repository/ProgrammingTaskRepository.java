package ua.kpi.grader.course.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ua.kpi.grader.course.entity.ProgrammingTask;

public interface ProgrammingTaskRepository extends JpaRepository<ProgrammingTask, Long> {
}
