package ua.kpi.grader.gitlab.dto;

import jakarta.validation.constraints.NotBlank;
import ua.kpi.grader.course.entity.Language;

/**
 * Request to validate that code compiles successfully.
 *
 * @param solutionCode    the solution / function signature code
 * @param testFileContent the test file content (required for unit test mode, null for IO mode)
 * @param language        target language for compilation / syntax check;
 *                        required for the teacher validation route, optional for the per-assignment route
 *                        (falls back to the task's stored language)
 */
public record CompileRequest(
        @NotBlank String solutionCode,
        String testFileContent,
        Language language
) {
}
