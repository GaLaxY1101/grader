package ua.kpi.grader.group.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import ua.kpi.grader.user.dto.StudentInput;

import java.util.List;

/**
 * Request body for committing a batch of students into an existing group.
 */
public record BulkCommitRequest(

        @NotEmpty
        List<@Valid StudentInput> students
) {}
