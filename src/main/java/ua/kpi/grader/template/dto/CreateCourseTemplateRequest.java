package ua.kpi.grader.template.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCourseTemplateRequest(
        @NotBlank String name,
        String description
) {
}
