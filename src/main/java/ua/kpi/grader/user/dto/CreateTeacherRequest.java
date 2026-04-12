package ua.kpi.grader.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateTeacherRequest(

        @NotNull
        Long userId,

        @Size(max = 100)
        String department,

        @Size(max = 100)
        String academicDegree
) {}
