package ua.kpi.grader.template.mapper;

import org.springframework.stereotype.Component;
import ua.kpi.grader.course.entity.Assignment;
import ua.kpi.grader.course.entity.Course;
import ua.kpi.grader.course.entity.ProgrammingTask;
import ua.kpi.grader.course.entity.TestCase;
import ua.kpi.grader.template.entity.TemplateAssignment;
import ua.kpi.grader.template.entity.TemplateProgrammingTask;
import ua.kpi.grader.user.entity.Teacher;

/**
 * Snapshots a template assignment into a concrete course Assignment.
 * The resulting graph is unmanaged and ready to be persisted via a cascade save.
 * Deadlines are left null so the teacher can set them per course instance.
 */
@Component
public class TemplateToCourseMapper {

    public Assignment toAssignment(TemplateAssignment source, Course course, Teacher createdBy) {
        Assignment assignment = Assignment.builder()
                .course(course)
                .title(source.getTitle())
                .description(source.getDescription())
                .maxScore(source.getMaxScore())
                .createdBy(createdBy)
                .build();

        TemplateProgrammingTask sourceTask = source.getProgrammingTask();
        if (sourceTask != null) {
            assignment.setProgrammingTask(toProgrammingTask(sourceTask, assignment));
        }
        return assignment;
    }

    private ProgrammingTask toProgrammingTask(TemplateProgrammingTask source, Assignment assignment) {
        ProgrammingTask task = ProgrammingTask.builder()
                .language(source.getLanguage())
                .testMode(source.getTestMode())
                .ciConfigTemplate(source.getCiConfigTemplate())
                .functionSignature(source.getFunctionSignature())
                .testFileContent(source.getTestFileContent())
                .build();
        task.setAssignment(assignment);

        source.getTestCases().forEach(tc -> task.getTestCases().add(
                TestCase.builder()
                        .programmingTask(task)
                        .name(tc.getName())
                        .testType(tc.getTestType())
                        .input(tc.getInput())
                        .expectedOutput(tc.getExpectedOutput())
                        .build()));
        return task;
    }
}
