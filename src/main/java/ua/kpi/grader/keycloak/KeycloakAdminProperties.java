package ua.kpi.grader.keycloak;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Keycloak Admin REST API client.
 * Credentials are read from environment variables with safe defaults for local dev.
 */
@ConfigurationProperties(prefix = "keycloak.admin")
public record KeycloakAdminProperties(
        String username,
        String password,
        String clientId
) {}
