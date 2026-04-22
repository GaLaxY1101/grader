package ua.kpi.grader.user.dto;

import jakarta.validation.constraints.*;
import ua.kpi.grader.user.entity.Role;

import java.time.LocalDate;

public record CreateUserRequest(

        @NotBlank @Email
        String email,

        @NotBlank @Size(max = 100)
        String firstName,

        @NotBlank @Size(max = 100)
        String lastName,

        @Size(max = 20)
        String phone,

        LocalDate dateOfBirth,

        @NotNull
        Role role
) {}
