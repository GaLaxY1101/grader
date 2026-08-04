package ua.kpi.grader.template.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateCourseTemplateRequest(
        @NotBlank String name,
        String description
) {
}
