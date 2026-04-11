package ua.kpi.grader.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateUserRequest(

        @NotBlank @Size(max = 100)
        String firstName,

        @NotBlank @Size(max = 100)
        String lastName,

        @Size(max = 20)
        String phone,

        LocalDate dateOfBirth,

        boolean isActive
) {}
