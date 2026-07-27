package ua.kpi.grader.course.dto;

import ua.kpi.grader.course.entity.CourseEnrollment;
import ua.kpi.grader.group.entity.AcademicGroup;

import java.time.OffsetDateTime;

public record EnrolledStudentResponse(
        Long studentId,
        String firstName,
        String lastName,
        String email,
        OffsetDateTime enrolledAt,
        String status,
        Long groupId,
        String groupCode
) {
    public static EnrolledStudentResponse from(CourseEnrollment enrollment) {
        return from(enrollment, null);
    }

    public static EnrolledStudentResponse from(CourseEnrollment enrollment, AcademicGroup activeGroup) {
        return new EnrolledStudentResponse(
                enrollment.getStudent().getId(),
                enrollment.getStudent().getUser().getFirstName(),
                enrollment.getStudent().getUser().getLastName(),
                enrollment.getStudent().getUser().getEmail(),
                enrollment.getEnrolledAt(),
                enrollment.getStatus(),
                activeGroup == null ? null : activeGroup.getId(),
                activeGroup == null ? null : activeGroup.getCode()
        );
    }
}
