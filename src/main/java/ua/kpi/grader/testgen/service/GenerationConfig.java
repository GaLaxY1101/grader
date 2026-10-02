package ua.kpi.grader.testgen.service;

import ua.kpi.grader.testgen.config.TestGenProperties;
import ua.kpi.grader.testgen.dto.GenerationOverrides;

/**
 * Effective settings of one generation run (defaults from {@code testgen.*} plus overrides).
 * Stored as JSON in {@code test_generation_jobs.config}.
 *
 * @param model            Ollama model tag
 * @param maxIterations    repair iterations after the initial generation; 0 = single-shot baseline
 * @param mutationFeedback whether surviving feedback-set mutants are sent to the LLM
 * @param temperature      sampling temperature
 * @param seed             LLM sampling seed and mutant pool seed; null = server-chosen sampling, pool seed 0
 * @param minTestCount     minimum test count enforced by the anti-cheat guard
 * @param maxMutants       mutant pool size (feedback + held-out)
 * @param pruneFailing     after the loop, remove tests that still fail on the reference solution
 */
public record GenerationConfig(
        String model,
        int maxIterations,
        boolean mutationFeedback,
        double temperature,
        Integer seed,
        int minTestCount,
        int maxMutants,
        boolean pruneFailing
) {

    /**
     * Builds the configuration from application properties.
     */
    public static GenerationConfig defaults(TestGenProperties properties) {
        return new GenerationConfig(
                properties.ollama().model(),
                properties.maxIterations(),
                properties.mutationFeedback(),
                properties.temperature(),
                null,
                properties.minTestCount(),
                properties.maxMutants(),
                properties.pruneFailing());
    }

    /**
     * Returns a copy with the non-null override values applied.
     */
    public GenerationConfig withOverrides(GenerationOverrides overrides) {
        if (overrides == null) {
            return this;
        }
        return new GenerationConfig(
                overrides.model() != null ? overrides.model() : model,
                overrides.maxIterations() != null ? overrides.maxIterations() : maxIterations,
                overrides.mutationFeedback() != null ? overrides.mutationFeedback() : mutationFeedback,
                overrides.temperature() != null ? overrides.temperature() : temperature,
                overrides.seed() != null ? overrides.seed() : seed,
                minTestCount,
                maxMutants,
                overrides.pruneFailing() != null ? overrides.pruneFailing() : pruneFailing);
    }

    /** Seed used for the mutant pool, so the A/B split is reproducible. */
    public long poolSeed() {
        return seed != null ? seed : 0L;
    }
}
