package ua.kpi.grader.common.exception;

/**
 * Thrown when a call to the Keycloak Admin REST API fails.
 */
public class KeycloakIntegrationException extends RuntimeException {

    public KeycloakIntegrationException(String message) {
        super(message);
    }

    public KeycloakIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
