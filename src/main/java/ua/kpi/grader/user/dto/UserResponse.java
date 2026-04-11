package ua.kpi.grader.user.dto;

import ua.kpi.grader.user.entity.Role;
import ua.kpi.grader.user.entity.User;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record UserResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        String phone,
        LocalDate dateOfBirth,
        Role role,
        boolean isActive,
        OffsetDateTime createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getPhone(),
                user.getDateOfBirth(),
                user.getRole(),
                user.isActive(),
                user.getCreatedAt()
        );
    }
}
