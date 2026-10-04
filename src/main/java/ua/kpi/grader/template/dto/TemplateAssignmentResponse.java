package ua.kpi.grader.template.dto;

import ua.kpi.grader.course.dto.ProgrammingTaskDetails;
import ua.kpi.grader.template.entity.TemplateAssignment;
import ua.kpi.grader.template.entity.TemplateProgrammingTask;

import java.time.OffsetDateTime;

public record TemplateAssignmentResponse(
        Long id,
        Long templateId,
        String title,
        String description,
        Integer maxScore,
        boolean codeCheckEnabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        ProgrammingTaskDetails programmingTask,
        int attachmentCount
) {
    public static TemplateAssignmentResponse from(TemplateAssignment assignment) {
        return new TemplateAssignmentResponse(
                assignment.getId(),
                assignment.getTemplate().getId(),
                assignment.getTitle(),
                assignment.getDescription(),
                assignment.getMaxScore(),
                assignment.isCodeCheckEnabled(),
                assignment.getCreatedAt(),
                assignment.getUpdatedAt(),
                toProgrammingTaskDetails(assignment.getProgrammingTask()),
                assignment.getAttachments() != null ? assignment.getAttachments().size() : 0
        );
    }

    private static ProgrammingTaskDetails toProgrammingTaskDetails(TemplateProgrammingTask task) {
        if (task == null) return null;
        return new ProgrammingTaskDetails(
                task.getLanguage(),
                task.getTestMode(),
                task.getCiConfigTemplate(),
                task.getFunctionSignature(),
                task.getTestFileContent(),
                null,
                task.getFeedbackLevel()
        );
    }
}
