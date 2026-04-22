package ua.kpi.grader.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {

    /**
     * Returns the Keycloak user UUID (JWT subject claim).
     */
    public String getUserId() {
        return getJwt().getSubject();
    }

    /**
     * Returns the user's email from the JWT email claim.
     */
    public String getEmail() {
        return getJwt().getClaimAsString("email");
    }

    /**
     * Returns the user's first name from the JWT given_name claim.
     */
    public String getFirstName() {
        return getJwt().getClaimAsString("given_name");
    }

    /**
     * Returns the user's last name from the JWT family_name claim.
     */
    public String getLastName() {
        return getJwt().getClaimAsString("family_name");
    }

    /**
     * Returns true if the current user has the specified role.
     */
    public boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }

    private Jwt getJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (Jwt) auth.getPrincipal();
    }
}
