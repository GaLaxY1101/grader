package ua.kpi.grader.course.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

public record UpdateAssignmentRequest(
        @NotBlank String title,
        String description,
        Integer maxScore,
        LocalDateTime deadline
) {
}
