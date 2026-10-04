package ua.kpi.grader.template.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import ua.kpi.grader.course.dto.ProgrammingTaskDetails;

public record CreateTemplateAssignmentRequest(
        @NotBlank String title,
        String description,
        Integer maxScore,
        @Valid ProgrammingTaskDetails programmingTask
) {
}
