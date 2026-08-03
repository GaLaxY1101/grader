package ua.kpi.grader.submission.dto;

import jakarta.validation.constraints.Min;

public record UpdateGradeRequest(
        @Min(value = 0, message = "Grade must be non-negative")
        Integer grade
) {}
