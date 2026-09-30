package ua.kpi.grader.course.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import ua.kpi.grader.course.entity.AssignmentType;

import java.time.LocalDateTime;

public record CreateAssignmentRequest(
        @NotBlank String title,
        String description,
        Integer maxScore,
        LocalDateTime deadline,
        @Valid ProgrammingTaskDetails programmingTask,
        AssignmentType type
) {
}
