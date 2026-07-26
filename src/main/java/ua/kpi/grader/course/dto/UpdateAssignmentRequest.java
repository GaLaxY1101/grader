package ua.kpi.grader.course.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record UpdateAssignmentRequest(
        @NotBlank String title,
        String description,
        @NotNull @Min(1) Integer maxScore,
        LocalDateTime deadline,
        /**
         * Desired state of the assignment's optional code check.
         * Non-null: create or update the ProgrammingTask.
         * Null: remove any existing ProgrammingTask (orphanRemoval deletes the row).
         */
        @Valid ProgrammingTaskDetails programmingTask
) {
}
