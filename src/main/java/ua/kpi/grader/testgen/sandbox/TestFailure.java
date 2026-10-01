package ua.kpi.grader.testgen.sandbox;

/**
 * A single failing test reported by the sandbox.
 *
 * @param name    test name (pytest function or C/C++ test name)
 * @param message failure reason, truncated
 */
public record TestFailure(String name, String message) {}
