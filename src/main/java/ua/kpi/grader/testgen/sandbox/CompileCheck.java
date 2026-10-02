package ua.kpi.grader.testgen.sandbox;

/**
 * Outcome of a compile-only sandbox run ({@link SandboxRunner#compileOnly}).
 *
 * @param success true if the compiler accepted the code
 * @param output  compiler output, or null when it printed nothing
 */
public record CompileCheck(boolean success, String output) {}
