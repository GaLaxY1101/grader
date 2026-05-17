package ua.kpi.grader.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import ua.kpi.grader.course.entity.TestCase;
import ua.kpi.grader.course.entity.TestType;

public record TestCaseDetails(
        @NotBlank String name,
        @NotNull TestType testType,
        String input,
        String expectedOutput
) {
    public static TestCaseDetails from(TestCase tc) {
        return new TestCaseDetails(
                tc.getName(),
                tc.getTestType(),
                tc.getInput(),
                tc.getExpectedOutput()
        );
    }
}
