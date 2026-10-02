package ua.kpi.grader.testgen.service;

import ua.kpi.grader.testgen.mutation.MutantPool;

import java.util.List;

/**
 * Result of a complete generation run.
 *
 * @param success          true if a valid test file was produced
 * @param finalTestContent latest valid test file; null if none
 * @param lastTestContent  test file the loop ended with (may be invalid); used by the evaluation
 * @param errorMessage     reason for failure; null on success
 * @param iterations       all LLM calls in order (iteration 0 = initial generation)
 * @param mutantPool       mutants used for feedback (A) and held-out measurement (B)
 * @param totalDurationMs  wall-clock time of the whole run
 */
public record GenerationOutcome(
        boolean success,
        String finalTestContent,
        String lastTestContent,
        String errorMessage,
        List<IterationRecord> iterations,
        MutantPool mutantPool,
        long totalDurationMs
) {}
