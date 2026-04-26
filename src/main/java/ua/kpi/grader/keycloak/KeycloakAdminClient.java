package ua.kpi.grader.keycloak;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import ua.kpi.grader.common.exception.KeycloakIntegrationException;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Client for the Keycloak Admin REST API.
 * Handles user provisioning: create account, assign realm role, trigger password-reset email.
 *
 * <p>Authentication uses the Resource Owner Password Credentials grant against the master realm
 * with the {@code admin-cli} client. A fresh token is fetched per operation — admin calls
 * are infrequent enough that token caching adds no meaningful value here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakAdminClient {

    private static final String MASTER_REALM = "master";

    private final KeycloakAdminProperties props;
    private final RestClient restClient = RestClient.create();

    @Value("${keycloak.base-url}")
    private String keycloakBaseUrl;

    @Value("${keycloak.realm}")
    private String realm;

    // ─── Internal DTOs ─────────────────────────────────────────────────────

    private record TokenResponse(String access_token) {}

    private record KeycloakUserRepresentation(
            String username,
            String email,
            String firstName,
            String lastName,
            boolean enabled,
            boolean emailVerified
    ) {}

    private record RoleRepresentation(String id, String name) {}

    // ─── Public API ────────────────────────────────────────────────────────

    /**
     * Creates a user in Keycloak and returns their Keycloak user UUID.
     *
     * @param email     the user's email (used as username too)
     * @param firstName first name
     * @param lastName  last name
     * @return the Keycloak user UUID extracted from the Location header
     * @throws KeycloakIntegrationException if the Admin API call fails
     */
    public String createUser(String email, String firstName, String lastName) {
        String token = getAdminToken();

        KeycloakUserRepresentation body = new KeycloakUserRepresentation(
                email, email, firstName, lastName, true, true
        );

        try {
            ResponseEntity<Void> response = restClient.post()
                    .uri(keycloakBaseUrl + "/admin/realms/" + realm + "/users")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            URI location = response.getHeaders().getLocation();
            if (location == null) {
                throw new KeycloakIntegrationException("Keycloak did not return a Location header after user creation");
            }

            String path = location.getPath();
            String keycloakUserId = path.substring(path.lastIndexOf('/') + 1);
            log.debug("Created Keycloak user {} with id {}", email, keycloakUserId);
            return keycloakUserId;

        } catch (RestClientException ex) {
            throw new KeycloakIntegrationException("Failed to create user in Keycloak: " + ex.getMessage(), ex);
        }
    }

    /**
     * Assigns a realm role to a Keycloak user.
     * The role must already exist in the realm (e.g. STUDENT, TEACHER, ADMIN).
     *
     * @param keycloakUserId the Keycloak user UUID
     * @param roleName       the exact realm role name
     * @throws KeycloakIntegrationException if the role does not exist or the API call fails
     */
    public void assignRealmRole(String keycloakUserId, String roleName) {
        String token = getAdminToken();
        RoleRepresentation role = getRealmRole(token, roleName);

        try {
            restClient.post()
                    .uri(keycloakBaseUrl + "/admin/realms/" + realm + "/users/" + keycloakUserId + "/role-mappings/realm")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(List.of(role))
                    .retrieve()
                    .toBodilessEntity();

            log.debug("Assigned role {} to Keycloak user {}", roleName, keycloakUserId);

        } catch (RestClientException ex) {
            throw new KeycloakIntegrationException(
                    "Failed to assign role '" + roleName + "' to user " + keycloakUserId + ": " + ex.getMessage(), ex);
        }
    }

    /**
     * Triggers a password-reset email for the given Keycloak user.
     * Requires Keycloak to have an SMTP server configured — in local dev this may silently fail.
     *
     * @param keycloakUserId the Keycloak user UUID
     */
    public void sendPasswordResetEmail(String keycloakUserId) {
        String token = getAdminToken();

        try {
            restClient.put()
                    .uri(keycloakBaseUrl + "/admin/realms/" + realm + "/users/" + keycloakUserId + "/execute-actions-email")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(List.of("UPDATE_PASSWORD"))
                    .retrieve()
                    .toBodilessEntity();

            log.info("Password reset email triggered for Keycloak user {}", keycloakUserId);

        } catch (RestClientException ex) {
            // Email delivery failure must not block user creation — log and continue.
            // In local dev this is expected when no SMTP server is configured.
            log.warn("Could not send password reset email for user {} (SMTP not configured?): {}",
                    keycloakUserId, ex.getMessage());
        }
    }

    // ─── Private helpers ───────────────────────────────────────────────────

    /**
     * Obtains a short-lived admin token from the master realm using ROPC grant.
     */
    private String getAdminToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", props.clientId());
        form.add("username", props.username());
        form.add("password", props.password());

        try {
            TokenResponse response = restClient.post()
                    .uri(keycloakBaseUrl + "/realms/" + MASTER_REALM + "/protocol/openid-connect/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);

            if (response == null || response.access_token() == null) {
                throw new KeycloakIntegrationException("Received empty token response from Keycloak");
            }
            return response.access_token();

        } catch (RestClientException ex) {
            throw new KeycloakIntegrationException("Failed to obtain Keycloak admin token: " + ex.getMessage(), ex);
        }
    }

    /**
     * Fetches a realm role by name.
     */
    @SuppressWarnings("unchecked")
    private RoleRepresentation getRealmRole(String token, String roleName) {
        try {
            Map<String, Object> raw = restClient.get()
                    .uri(keycloakBaseUrl + "/admin/realms/" + realm + "/roles/" + roleName)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .body(Map.class);

            if (raw == null || raw.get("id") == null) {
                throw new KeycloakIntegrationException("Role '" + roleName + "' not found in realm '" + realm + "'");
            }

            return new RoleRepresentation(
                    (String) raw.get("id"),
                    (String) raw.get("name")
            );

        } catch (RestClientException ex) {
            throw new KeycloakIntegrationException(
                    "Role '" + roleName + "' not found in Keycloak realm '" + realm + "': " + ex.getMessage(), ex);
        }
    }
}
