package ua.kpi.grader.course.dto;

import ua.kpi.grader.course.entity.Assignment;

import java.time.OffsetDateTime;

public record AssignmentResponse(
        Long id,
        Long courseId,
        String title,
        String description,
        Integer maxScore,
        OffsetDateTime deadline,
        boolean isActive,
        Long createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        ProgrammingTaskDetails programmingTask,
        FileUploadTaskDetails fileUploadTask
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
                assignment.getCreatedBy().getId(),
                assignment.getCreatedAt(),
                assignment.getUpdatedAt(),
                ProgrammingTaskDetails.from(assignment.getProgrammingTask()),
                FileUploadTaskDetails.from(assignment.getFileUploadTask())
        );
    }
}
