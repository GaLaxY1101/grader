package ua.kpi.grader.template.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import ua.kpi.grader.course.dto.ProgrammingTaskDetails;
import ua.kpi.grader.course.entity.AssignmentType;

public record CreateTemplateAssignmentRequest(
        @NotBlank String title,
        String description,
        Integer maxScore,
        @Valid ProgrammingTaskDetails programmingTask,
        AssignmentType type
) {
}
