package ua.kpi.grader.testgen.sandbox;

/**
 * Thrown when the Docker sandbox cannot be started (Docker missing or daemon down).
 * Mapped to 503 Service Unavailable.
 */
public class SandboxUnavailableException extends RuntimeException {

    public SandboxUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
