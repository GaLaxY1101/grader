package ua.kpi.grader.testgen.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for AI test generation, bound from {@code testgen.*} in application.yml.
 *
 * @param provider         active LLM backend; selects which client bean the context registers
 * @param ollama           settings for the local Ollama backend (used when {@code provider = OLLAMA})
 * @param gemini           settings for the hosted Google Gemini API (used when {@code provider = GEMINI})
 * @param temperature      default sampling temperature
 * @param maxIterations    maximum self-repair iterations (0 = single-shot)
 * @param minTestCount     minimum number of tests a generated file must keep
 * @param mutationFeedback whether surviving mutants are fed back to the LLM
 * @param maxMutants       maximum number of mutants generated per reference solution
 * @param pruneFailing     after the loop, remove tests that still fail on the reference solution
 */
@ConfigurationProperties(prefix = "testgen")
public record TestGenProperties(
        Provider provider,
        Ollama ollama,
        Gemini gemini,
        double temperature,
        int maxIterations,
        int minTestCount,
        boolean mutationFeedback,
        int maxMutants,
        boolean pruneFailing,
        Sandbox sandbox
) {

    /** Active LLM backend. */
    public enum Provider {
        OLLAMA, GEMINI
    }

    /**
     * Default model tag for the active provider. Used when a job does not override it.
     */
    public String defaultModel() {
        return provider == Provider.GEMINI ? gemini.model() : ollama.model();
    }

    /**
     * @param baseUrl        Ollama server URL
     * @param model          model tag, e.g. {@code qwen2.5-coder:3b}
     * @param timeoutSeconds read timeout for one chat call (includes cold model load)
     * @param keepAlive      how long Ollama keeps the model loaded after a call, e.g. {@code 30m}
     * @param numCtx         context window in tokens; the repair loop sends conversation history
     */
    public record Ollama(
            String baseUrl,
            String model,
            int timeoutSeconds,
            String keepAlive,
            int numCtx
    ) {}

    /**
     * @param baseUrl         Gemini API base URL ({@code https://generativelanguage.googleapis.com})
     * @param apiKey          Google AI Studio API key; must be set when {@code provider = GEMINI}
     * @param model           model tag, e.g. {@code gemini-2.0-flash}
     * @param timeoutSeconds  read timeout for one chat call
     * @param maxOutputTokens upper bound on tokens generated per call
     * @param thinkingBudget  tokens the model may spend on reasoning before answering;
     *                        {@code 0} disables thinking (fastest), ignored by non-thinking models
     */
    public record Gemini(
            String baseUrl,
            String apiKey,
            String model,
            int timeoutSeconds,
            int maxOutputTokens,
            int thinkingBudget
    ) {}

    /**
     * @param cppImage       Docker image for C/C++ tests
     * @param pythonImage    Docker image for Python tests
     * @param timeoutSeconds wall-clock limit for one sandbox run
     * @param mutantTimeoutSeconds limit for running the tests against one mutant
     *                             (mutants often loop forever, so this is kept short)
     * @param memory         container memory limit, Docker syntax (e.g. {@code 256m})
     * @param cpus           container CPU limit, Docker syntax (e.g. {@code 1})
     */
    public record Sandbox(
            String cppImage,
            String pythonImage,
            int timeoutSeconds,
            int mutantTimeoutSeconds,
            String memory,
            String cpus
    ) {}
}
