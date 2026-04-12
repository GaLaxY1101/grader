package ua.kpi.grader.user.dto;

import jakarta.validation.constraints.NotNull;

public record CreateStudentRequest(

        @NotNull
        Long userId
) {}
