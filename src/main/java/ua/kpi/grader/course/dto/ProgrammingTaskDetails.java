package ua.kpi.grader.course.dto;

import jakarta.validation.constraints.NotNull;
import ua.kpi.grader.course.entity.Language;
import ua.kpi.grader.course.entity.ProgrammingTask;
import ua.kpi.grader.course.entity.TestMode;

public record ProgrammingTaskDetails(
        @NotNull Language language,
        TestMode testMode,
        String ciConfigTemplate,
        String functionSignature,
        String testFileContent
) {
    public static ProgrammingTaskDetails from(ProgrammingTask task) {
        if (task == null) return null;
        return new ProgrammingTaskDetails(
                task.getLanguage(),
                task.getTestMode(),
                task.getCiConfigTemplate(),
                task.getFunctionSignature(),
                task.getTestFileContent()
        );
    }
}
