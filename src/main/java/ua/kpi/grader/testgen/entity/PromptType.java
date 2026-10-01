package ua.kpi.grader.testgen.entity;

/**
 * Kind of prompt sent to the LLM in one iteration of the self-repair loop.
 */
public enum PromptType {
    /** Initial generation from the task specification. */
    GENERATE,
    /** Tests did not compile or import; compiler errors are fed back. */
    REPAIR_COMPILE,
    /** Some tests failed on the (correct) reference solution. */
    REPAIR_FAILING,
    /** All tests pass but a mutant survived; its diff is fed back. */
    KILL_MUTANT
}
