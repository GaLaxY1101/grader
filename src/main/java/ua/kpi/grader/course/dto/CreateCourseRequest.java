package ua.kpi.grader.course.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateCourseRequest(
        @NotBlank String name,
        String description,
        @NotNull Integer academicYear,
        @NotNull @Min(1) @Max(2) Integer semester,
        LocalDate startDate,
        LocalDate endDate
) {
}
