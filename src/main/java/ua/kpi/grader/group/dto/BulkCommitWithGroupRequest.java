package ua.kpi.grader.group.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import ua.kpi.grader.user.dto.StudentInput;

import java.util.List;

/**
 * Request body for creating a new group and committing a batch of students
 * into it in a single transaction.
 */
public record BulkCommitWithGroupRequest(

        @NotNull @Valid
        CreateGroupRequest group,

        @NotEmpty
        List<@Valid StudentInput> students
) {}
