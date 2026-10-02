package ua.kpi.grader.testgen.dto;

import ua.kpi.grader.testgen.entity.PromptType;
import ua.kpi.grader.testgen.entity.TestGenerationIteration;

/**
 * Metrics of one iteration of the self-repair loop. Raw prompts and LLM output stay in the database.
 *
 * @param mutantsKilled mutants detected by the tests (feedback set); null when the tests were not valid
 * @param accepted      false if the candidate was rejected by the anti-cheat guard
 */
public record IterationResponse(
        int iterationNo,
        PromptType promptType,
        Boolean compileOk,
        Integer refPassed,
        Integer refTotal,
        Integer mutantsKilled,
        Integer mutantsTotal,
        Double coveragePct,
        Integer testCount,
        Boolean accepted,
        Long durationMs
) {
    public static IterationResponse from(TestGenerationIteration iteration) {
        return new IterationResponse(
                iteration.getIterationNo(),
                iteration.getPromptType(),
                iteration.getCompileOk(),
                iteration.getRefPassed(),
                iteration.getRefTotal(),
                iteration.getMutantsKilled(),
                iteration.getMutantsTotal(),
                iteration.getCoveragePct(),
                iteration.getTestCount(),
                iteration.getAccepted(),
                iteration.getDurationMs());
    }
}
