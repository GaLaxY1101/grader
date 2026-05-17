package ua.kpi.grader.course.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import ua.kpi.grader.course.entity.Language;
import ua.kpi.grader.course.entity.ProgrammingTask;
import ua.kpi.grader.course.entity.TestMode;

import java.util.List;

public record ProgrammingTaskDetails(
        @NotNull Language language,
        TestMode testMode,
        String ciConfigTemplate,
        String functionSignature,
        String testFileContent,
        @Valid List<TestCaseDetails> testCases
) {
    public static ProgrammingTaskDetails from(ProgrammingTask task) {
        if (task == null) return null;
        return new ProgrammingTaskDetails(
                task.getLanguage(),
                task.getTestMode(),
                task.getCiConfigTemplate(),
                task.getFunctionSignature(),
                task.getTestFileContent(),
                task.getTestCases().stream().map(TestCaseDetails::from).toList()
        );
    }
}
