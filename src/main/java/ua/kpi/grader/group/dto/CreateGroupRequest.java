package ua.kpi.grader.group.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateGroupRequest(
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 100) String faculty,
        @Size(max = 100) String speciality,
        @NotNull Integer yearOfCreation
) {}
