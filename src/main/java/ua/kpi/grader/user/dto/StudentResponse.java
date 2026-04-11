package ua.kpi.grader.user.dto;

import ua.kpi.grader.user.entity.Student;

import java.time.OffsetDateTime;

public record StudentResponse(
        Long id,
        Long userId,
        String firstName,
        String lastName,
        String email,
        OffsetDateTime createdAt
) {
    public static StudentResponse from(Student student) {
        return new StudentResponse(
                student.getId(),
                student.getUser().getId(),
                student.getUser().getFirstName(),
                student.getUser().getLastName(),
                student.getUser().getEmail(),
                student.getCreatedAt()
        );
    }
}
