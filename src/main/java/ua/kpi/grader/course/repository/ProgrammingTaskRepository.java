package ua.kpi.grader.course.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ua.kpi.grader.course.entity.ProgrammingTask;

import java.util.Optional;

public interface ProgrammingTaskRepository extends JpaRepository<ProgrammingTask, Long> {

    Optional<ProgrammingTask> findByAssignmentId(Long assignmentId);
}
