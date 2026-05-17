package ua.kpi.grader.gitlab.dto;

/**
 * Response from a compilation validation check.
 *
 * @param success true if code compiles without errors
 * @param output  compiler output (errors/warnings), null if clean
 */
public record CompileResponse(boolean success, String output) {
}
