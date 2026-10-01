package ua.kpi.grader.testgen.client;

/**
 * Thrown when the LLM server cannot be reached, times out or returns an error.
 * Mapped to 503 Service Unavailable.
 */
public class LlmUnavailableException extends RuntimeException {

    public LlmUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
