package ua.kpi.grader.template.dto;

import jakarta.validation.constraints.NotNull;

public record CreateTemplateShareRequest(
        @NotNull Long teacherId
) {
}
