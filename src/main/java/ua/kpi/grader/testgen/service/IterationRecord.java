package ua.kpi.grader.testgen.service;

import ua.kpi.grader.testgen.entity.PromptType;

/**
 * Everything observed in one LLM call of the loop: the prompt, the raw answer, the extracted
 * test file and its sandbox metrics. Metrics describe the candidate produced in this iteration,
 * whether or not it was accepted.
 *
 * @param feedback       compiler errors, failing tests or mutant diff included in the prompt; null for GENERATE
 * @param accepted       false if the anti-cheat guard rejected the candidate
 * @param temperature    sampling temperature used for this call; null for PRUNE_FAILING
 * @param mutantsKilled  feedback-set (A) mutants killed; null when the tests are not valid
 * @param heldoutKilled  held-out (B) mutants killed; null when the tests are not valid
 */
public record IterationRecord(
        int iterationNo,
        PromptType promptType,
        String prompt,
        String llmOutput,
        String testContent,
        String feedback,
        boolean compileOk,
        int refPassed,
        int refTotal,
        boolean valid,
        Integer mutantsKilled,
        Integer mutantsTotal,
        Integer heldoutKilled,
        Integer heldoutTotal,
        Double coveragePct,
        int testCount,
        boolean accepted,
        Double temperature,
        long durationMs,
        Integer promptTokens,
        Integer completionTokens
) {}
