package ua.kpi.grader.template.dto;

import ua.kpi.grader.course.dto.ProgrammingTaskDetails;
import ua.kpi.grader.course.dto.TestCaseDetails;
import ua.kpi.grader.template.entity.TemplateAssignment;
import ua.kpi.grader.template.entity.TemplateProgrammingTask;

import java.time.OffsetDateTime;

public record TemplateAssignmentResponse(
        Long id,
        Long templateId,
        String title,
        String description,
        Integer maxScore,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        ProgrammingTaskDetails programmingTask
) {
    public static TemplateAssignmentResponse from(TemplateAssignment assignment) {
        return new TemplateAssignmentResponse(
                assignment.getId(),
                assignment.getTemplate().getId(),
                assignment.getTitle(),
                assignment.getDescription(),
                assignment.getMaxScore(),
                assignment.getCreatedAt(),
                assignment.getUpdatedAt(),
                toProgrammingTaskDetails(assignment.getProgrammingTask())
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
                task.getTestCases().stream()
                        .map(tc -> new TestCaseDetails(tc.getName(), tc.getTestType(),
                                tc.getInput(), tc.getExpectedOutput()))
                        .toList()
        );
    }
}
