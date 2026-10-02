package ua.kpi.grader.testgen.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ua.kpi.grader.course.entity.Language;

/**
 * Starts AI test generation. Works for saved assignments and unsaved drafts.
 *
 * @param assignmentId      assignment the tests are for; null for an unsaved draft
 * @param taskDescription   task statement; this or {@code functionSignature} is required
 * @param functionSignature signature of the function under test
 * @param language          solution language
 * @param referenceSolution correct solution used to validate the generated tests
 * @param config            optional overrides of the generation settings
 */
public record StartTestGenerationRequest(
        Long assignmentId,
        @Size(max = 20_000) String taskDescription,
        @Size(max = 2_000) String functionSignature,
        @NotNull Language language,
        @NotBlank @Size(max = 50_000) String referenceSolution,
        @Valid GenerationOverrides config
) {}
