package ua.kpi.grader.template.mapper;

import org.springframework.stereotype.Component;
import ua.kpi.grader.course.dto.ProgrammingTaskDetails;
import ua.kpi.grader.course.entity.TestMode;
import ua.kpi.grader.template.entity.CourseTemplate;
import ua.kpi.grader.template.entity.TemplateAssignment;
import ua.kpi.grader.template.entity.TemplateProgrammingTask;

/**
 * Builds and clones the template content graph
 * (TemplateAssignment + TemplateProgrammingTask).
 */
@Component
public class TemplateContentMapper {

    /**
     * Builds a new {@link TemplateProgrammingTask} attached to the given assignment
     * from a {@link ProgrammingTaskDetails} payload. Validates required fields.
     */
    public TemplateProgrammingTask buildProgrammingTask(TemplateAssignment assignment,
                                                        ProgrammingTaskDetails details) {
        validate(details);

        TemplateProgrammingTask task = TemplateProgrammingTask.builder()
                .language(details.language())
                .testMode(TestMode.UNIT_TEST)
                .ciConfigTemplate(details.ciConfigTemplate())
                .functionSignature(details.functionSignature())
                .testFileContent(details.testFileContent())
                .build();
        task.setAssignment(assignment);
        return task;
    }

    /**
     * Updates an existing {@link TemplateProgrammingTask} in place from a
     * {@link ProgrammingTaskDetails} payload.
     */
    public void updateProgrammingTask(TemplateProgrammingTask task, ProgrammingTaskDetails details) {
        validate(details);
        task.update(TestMode.UNIT_TEST, details.functionSignature(),
                details.testFileContent(), details.ciConfigTemplate());
    }

    /**
     * Deep-clones a template assignment (including its programming task) under
     * the given destination template. The returned entity is unmanaged and ready
     * to be persisted via a cascade save.
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
        return copy;
    }

    private void validate(ProgrammingTaskDetails details) {
        if (details.functionSignature() == null || details.functionSignature().isBlank()) {
            throw new IllegalArgumentException("Function signature is required for programming tasks");
        }
        if (details.language() == null) {
            throw new IllegalArgumentException("Language is required for programming tasks");
        }
        if (details.testFileContent() == null || details.testFileContent().isBlank()) {
            throw new IllegalArgumentException("Test file content is required for unit test mode");
        }
    }
}
