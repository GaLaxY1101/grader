package ua.kpi.grader.group.dto;

import ua.kpi.grader.group.entity.GroupStudent;

import java.time.OffsetDateTime;

public record GroupStudentResponse(
        Long studentId,
        String firstName,
        String lastName,
        String email,
        OffsetDateTime enrolledAt
) {
    public static GroupStudentResponse from(GroupStudent gs) {
        return new GroupStudentResponse(
                gs.getStudent().getId(),
                gs.getStudent().getUser().getFirstName(),
                gs.getStudent().getUser().getLastName(),
                gs.getStudent().getUser().getEmail(),
                gs.getEnrolledAt()
        );
    }
}
