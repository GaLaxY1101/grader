package ua.kpi.grader.course.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

public record CreateAssignmentRequest(
        @NotBlank String title,
        String description,
        Integer maxScore,
        LocalDateTime deadline,
        @Valid ProgrammingTaskDetails programmingTask,
        @Valid FileUploadTaskDetails fileUploadTask
) {
}
