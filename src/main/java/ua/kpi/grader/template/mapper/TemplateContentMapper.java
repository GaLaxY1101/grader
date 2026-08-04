package ua.kpi.grader.template.mapper;

import org.springframework.stereotype.Component;
import ua.kpi.grader.course.dto.ProgrammingTaskDetails;
import ua.kpi.grader.course.entity.Language;
import ua.kpi.grader.course.entity.TestMode;
import ua.kpi.grader.template.entity.CourseTemplate;
import ua.kpi.grader.template.entity.TemplateAssignment;
import ua.kpi.grader.template.entity.TemplateProgrammingTask;
import ua.kpi.grader.template.entity.TemplateTestCase;

import java.util.List;

/**
 * Builds and clones the template content graph
 * (TemplateAssignment + TemplateProgrammingTask + TemplateTestCase).
 */
@Component
public class TemplateContentMapper {

    /**
     * Builds a new {@link TemplateProgrammingTask} attached to the given assignment
     * from a {@link ProgrammingTaskDetails} payload. Validates required fields
     * based on the effective test mode.
     */
    public TemplateProgrammingTask buildProgrammingTask(TemplateAssignment assignment,
                                                        ProgrammingTaskDetails details) {
        TestMode testMode = details.testMode() != null ? details.testMode() : TestMode.IO;
        validate(details, testMode);

        TemplateProgrammingTask task = TemplateProgrammingTask.builder()
                .language(details.language())
                .testMode(testMode)
                .ciConfigTemplate(details.ciConfigTemplate())
                .functionSignature(details.functionSignature())
                .testFileContent(details.testFileContent())
                .build();
        task.setAssignment(assignment);

        if (testMode == TestMode.IO && details.testCases() != null) {
            details.testCases().forEach(tc -> task.getTestCases().add(
                    TemplateTestCase.builder()
                            .programmingTask(task)
                            .name(tc.name())
                            .testType(tc.testType())
                            .input(tc.input())
                            .expectedOutput(tc.expectedOutput())
                            .build()));
        }
        return task;
    }

    /**
     * Updates an existing {@link TemplateProgrammingTask} in place from a
     * {@link ProgrammingTaskDetails} payload. Replaces the test-case list.
     */
    public void updateProgrammingTask(TemplateProgrammingTask task, ProgrammingTaskDetails details) {
        TestMode testMode = details.testMode() != null ? details.testMode() : task.getTestMode();
        validate(details, testMode);

        task.update(testMode, details.functionSignature(),
                details.testFileContent(), details.ciConfigTemplate());

        if (testMode == TestMode.IO && details.testCases() != null) {
            List<TemplateTestCase> replacements = details.testCases().stream()
                    .map(tc -> TemplateTestCase.builder()
                            .programmingTask(task)
                            .name(tc.name())
                            .testType(tc.testType())
                            .input(tc.input())
                            .expectedOutput(tc.expectedOutput())
                            .build())
                    .toList();
            task.replaceTestCases(replacements);
        } else if (testMode == TestMode.UNIT_TEST) {
            task.replaceTestCases(List.of());
        }
    }

    /**
     * Deep-clones a template assignment (including its programming task and test
     * cases) under the given destination template. The returned entity is
     * unmanaged and ready to be persisted via a cascade save.
     */
    public TemplateAssignment cloneAssignment(TemplateAssignment source, CourseTemplate destination) {
        TemplateAssignment copy = TemplateAssignment.builder()
                .template(destination)
                .title(source.getTitle())
                .description(source.getDescription())
                .maxScore(source.getMaxScore())
                .build();

        TemplateProgrammingTask sourceTask = source.getProgrammingTask();
        if (sourceTask != null) {
            copy.setProgrammingTask(cloneProgrammingTask(sourceTask, copy));
        }
        return copy;
    }

    private TemplateProgrammingTask cloneProgrammingTask(TemplateProgrammingTask source,
                                                         TemplateAssignment destination) {
        TemplateProgrammingTask copy = TemplateProgrammingTask.builder()
                .language(source.getLanguage())
                .testMode(source.getTestMode())
                .ciConfigTemplate(source.getCiConfigTemplate())
                .functionSignature(source.getFunctionSignature())
                .testFileContent(source.getTestFileContent())
                .build();
        copy.setAssignment(destination);

        source.getTestCases().forEach(tc -> copy.getTestCases().add(
                TemplateTestCase.builder()
                        .programmingTask(copy)
                        .name(tc.getName())
                        .testType(tc.getTestType())
                        .input(tc.getInput())
                        .expectedOutput(tc.getExpectedOutput())
                        .build()));
        return copy;
    }

    private void validate(ProgrammingTaskDetails details, TestMode testMode) {
        if (details.functionSignature() == null || details.functionSignature().isBlank()) {
            throw new IllegalArgumentException("Function signature is required for programming tasks");
        }
        if (testMode == TestMode.UNIT_TEST) {
            if (details.language() != Language.CPP) {
                throw new IllegalArgumentException("Unit test mode is only supported for C++");
            }
            if (details.testFileContent() == null || details.testFileContent().isBlank()) {
                throw new IllegalArgumentException("Test file content is required for unit test mode");
            }
        }
    }
}
