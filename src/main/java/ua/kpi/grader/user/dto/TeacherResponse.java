package ua.kpi.grader.user.dto;

import ua.kpi.grader.user.entity.Teacher;

import java.time.OffsetDateTime;

public record TeacherResponse(
        Long id,
        Long userId,
        String firstName,
        String lastName,
        String email,
        String department,
        String academicDegree,
        OffsetDateTime createdAt
) {
    public static TeacherResponse from(Teacher teacher) {
        return new TeacherResponse(
                teacher.getId(),
                teacher.getUser().getId(),
                teacher.getUser().getFirstName(),
                teacher.getUser().getLastName(),
                teacher.getUser().getEmail(),
                teacher.getDepartment(),
                teacher.getAcademicDegree(),
                teacher.getCreatedAt()
        );
    }
}
