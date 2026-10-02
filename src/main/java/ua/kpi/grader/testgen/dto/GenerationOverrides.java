package ua.kpi.grader.testgen.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/**
 * Optional per-request overrides of the {@code testgen.*} defaults. Null fields keep the default.
 *
 * @param maxIterations    repair iterations; 0 = single-shot generation without repair
 * @param mutationFeedback feed surviving mutants back to the LLM
 * @param temperature      sampling temperature
 * @param seed             sampling seed for reproducible runs
 * @param model            Ollama model tag
 * @param pruneFailing     remove tests that still fail on the reference solution after the loop
 */
public record GenerationOverrides(
        @Min(0) @Max(5) Integer maxIterations,
        Boolean mutationFeedback,
        @DecimalMin("0.0") @DecimalMax("2.0") Double temperature,
        Integer seed,
        @Pattern(regexp = "^[\\w.:/-]{1,100}$") String model,
        Boolean pruneFailing
) {}
