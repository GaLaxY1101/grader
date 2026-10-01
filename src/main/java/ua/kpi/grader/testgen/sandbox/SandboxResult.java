package ua.kpi.grader.testgen.sandbox;

import java.util.List;

/**
 * Outcome of running a test file against a solution inside the Docker sandbox.
 *
 * @param compiled      true if the test file compiled / imported successfully
 * @param compileOutput compiler or import error output (truncated), empty if none
 * @param passed        number of passing tests
 * @param failed        number of failing tests (including errors)
 * @param total         {@code passed + failed}
 * @param failures      details of failing tests
 * @param timedOut      true if the run was killed after the configured timeout
 * @param rawOutput     full container output (truncated)
 * @param coveragePct   line coverage of the solution in percent; null unless run with coverage
 */
public record SandboxResult(
        boolean compiled,
        String compileOutput,
        int passed,
        int failed,
        int total,
        List<TestFailure> failures,
        boolean timedOut,
        String rawOutput,
        Double coveragePct
) {

    /**
     * True if the tests compiled, did not time out, at least one test ran and none failed.
     */
    public boolean allPassed() {
        return compiled && !timedOut && total > 0 && failed == 0;
    }
}
