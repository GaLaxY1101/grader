package ua.kpi.grader.course.dto;

import jakarta.validation.constraints.NotBlank;
import ua.kpi.grader.course.entity.ProgrammingTask;

public record ProgrammingTaskDetails(
        @NotBlank String language,
        String gitlabProjectTemplate,
        String ciConfigTemplate
) {
    public static ProgrammingTaskDetails from(ProgrammingTask task) {
        if (task == null) return null;
        return new ProgrammingTaskDetails(
                task.getLanguage(),
                task.getGitlabProjectTemplate(),
                task.getCiConfigTemplate()
        );
    }
}
