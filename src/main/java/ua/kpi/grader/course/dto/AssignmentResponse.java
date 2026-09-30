package ua.kpi.grader.course.dto;

import ua.kpi.grader.course.entity.Assignment;
import ua.kpi.grader.course.entity.AssignmentType;

import java.time.OffsetDateTime;

public record AssignmentResponse(
        Long id,
        Long courseId,
        String title,
        String description,
        Integer maxScore,
        OffsetDateTime deadline,
        boolean isActive,
        AssignmentType type,
        Long createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        ProgrammingTaskDetails programmingTask,
        int attachmentCount
) {
    public static AssignmentResponse from(Assignment assignment) {
        return new AssignmentResponse(
                assignment.getId(),
                assignment.getCourse().getId(),
                assignment.getTitle(),
                assignment.getDescription(),
                assignment.getMaxScore(),
                assignment.getDeadline(),
                assignment.isActive(),
                assignment.getType(),
                assignment.getCreatedBy().getId(),
                assignment.getCreatedAt(),
                assignment.getUpdatedAt(),
                ProgrammingTaskDetails.from(assignment.getProgrammingTask()),
                assignment.getAttachments() != null ? assignment.getAttachments().size() : 0
        );
    }
}
