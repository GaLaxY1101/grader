package ua.kpi.grader.course.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateCourseRequest(
        @NotBlank String name,
        String description,
        @NotNull Integer academicYear,
        @NotNull @Min(1) @Max(2) Integer semester
) {
}
