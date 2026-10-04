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
        boolean codeCheckEnabled,
        Long createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        ProgrammingTaskDetails programmingTask,
        int attachmentCount
) {
    /**
     * Maps an assignment to its response DTO.
     *
     * @param includeReferenceSolution whether the caller may see the programming task's
     *                                 reference solution (TEACHER/ADMIN only)
     */
    public static AssignmentResponse from(Assignment assignment, boolean includeReferenceSolution) {
        return new AssignmentResponse(
                assignment.getId(),
                assignment.getCourse().getId(),
                assignment.getTitle(),
                assignment.getDescription(),
                assignment.getMaxScore(),
                assignment.getDeadline(),
                assignment.isActive(),
                assignment.isCodeCheckEnabled(),
                assignment.getCreatedBy().getId(),
                assignment.getCreatedAt(),
                assignment.getUpdatedAt(),
                ProgrammingTaskDetails.from(assignment.getProgrammingTask(), includeReferenceSolution),
                assignment.getAttachments() != null ? assignment.getAttachments().size() : 0
        );
    }
}
