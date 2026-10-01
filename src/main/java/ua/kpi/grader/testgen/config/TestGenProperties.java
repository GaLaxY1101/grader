package ua.kpi.grader.testgen.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for AI test generation, bound from {@code testgen.*} in application.yml.
 *
 * @param temperature      default sampling temperature
 * @param maxIterations    maximum self-repair iterations (0 = single-shot)
 * @param minTestCount     minimum number of tests a generated file must keep
 * @param mutationFeedback whether surviving mutants are fed back to the LLM
 * @param maxMutants       maximum number of mutants generated per reference solution
 */
@ConfigurationProperties(prefix = "testgen")
public record TestGenProperties(
        Ollama ollama,
        double temperature,
        int maxIterations,
        int minTestCount,
        boolean mutationFeedback,
        int maxMutants,
        Sandbox sandbox
) {

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
     * @param cppImage       Docker image for C/C++ tests
     * @param pythonImage    Docker image for Python tests
     * @param timeoutSeconds wall-clock limit for one sandbox run
     * @param memory         container memory limit, Docker syntax (e.g. {@code 256m})
     * @param cpus           container CPU limit, Docker syntax (e.g. {@code 1})
     */
    public record Sandbox(
            String cppImage,
            String pythonImage,
            int timeoutSeconds,
            String memory,
            String cpus
    ) {}
}
