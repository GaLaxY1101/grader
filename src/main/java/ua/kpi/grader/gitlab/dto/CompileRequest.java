package ua.kpi.grader.gitlab.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to validate that code compiles successfully.
 *
 * @param solutionCode    the solution / function signature code
 * @param testFileContent the test file content (required for unit test mode, null for IO mode)
 */
public record CompileRequest(
        @NotBlank String solutionCode,
        String testFileContent
) {
}
