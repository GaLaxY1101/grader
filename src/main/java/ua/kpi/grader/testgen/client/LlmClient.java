package ua.kpi.grader.testgen.client;

import java.util.List;

/**
 * Chat-style LLM provider abstraction. Implementations wrap a concrete backend
 * (Ollama for local inference, Gemini for the hosted Google API).
 */
public interface LlmClient {

    /**
     * Sends a chat conversation to the given model and returns the assistant reply.
     *
     * @param model       model identifier (e.g. {@code qwen2.5-coder:3b} or {@code gemini-2.0-flash})
     * @param messages    conversation so far (system, user and assistant turns)
     * @param temperature sampling temperature
     * @param seed        sampling seed for reproducibility; {@code null} lets the provider choose
     * @return assistant reply with token counts and duration
     * @throws LlmUnavailableException if the provider is unreachable, times out or returns an error
     */
    LlmResponse chat(String model, List<ChatMessage> messages, double temperature, Integer seed);
}
