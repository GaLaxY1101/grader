package ua.kpi.grader.course.dto;

import ua.kpi.grader.course.entity.CourseEnrollment;

import java.time.OffsetDateTime;

public record EnrolledStudentResponse(
        Long studentId,
        String firstName,
        String lastName,
        String email,
        OffsetDateTime enrolledAt,
        String status
) {
    public static EnrolledStudentResponse from(CourseEnrollment enrollment) {
        return new EnrolledStudentResponse(
                enrollment.getStudent().getId(),
                enrollment.getStudent().getUser().getFirstName(),
                enrollment.getStudent().getUser().getLastName(),
                enrollment.getStudent().getUser().getEmail(),
                enrollment.getEnrolledAt(),
                enrollment.getStatus()
        );
    }
}
