package ua.kpi.grader.testgen.client;

/**
 * Result of one LLM chat call.
 *
 * @param content          raw assistant message text
 * @param model            model that produced the answer
 * @param promptTokens     tokens in the prompt ({@code prompt_eval_count}); null if not reported
 * @param completionTokens tokens generated ({@code eval_count}); null if not reported
 * @param durationMs       wall-clock duration of the HTTP call
 */
public record LlmResponse(
        String content,
        String model,
        Integer promptTokens,
        Integer completionTokens,
        long durationMs
) {}
