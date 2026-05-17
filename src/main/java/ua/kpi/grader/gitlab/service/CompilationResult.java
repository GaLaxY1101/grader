package ua.kpi.grader.gitlab.service;

/**
 * Result of a local compilation check.
 *
 * @param success true if compilation succeeded (exit code 0)
 * @param output  compiler output (errors/warnings), null if clean compilation
 */
public record CompilationResult(boolean success, String output) {
}
