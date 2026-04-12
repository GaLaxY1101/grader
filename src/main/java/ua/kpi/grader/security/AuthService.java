package ua.kpi.grader.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import ua.kpi.grader.common.exception.InvalidCredentialsException;
import ua.kpi.grader.security.dto.LoginRequest;
import ua.kpi.grader.security.dto.LoginResponse;
import ua.kpi.grader.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /**
     * Authenticates a user by email and password and returns a JWT token.
     * Returns 401 for any of: unknown email, wrong password, inactive account.
     * A single vague error message prevents user enumeration.
     *
     * @param request the login credentials
     * @return a LoginResponse containing the signed JWT
     * @throws InvalidCredentialsException if email/password are wrong or the account is inactive
     */
    public LoginResponse login(LoginRequest request) {
        var user = userRepository.findByEmail(request.email())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .filter(u -> u.isActive())
                .orElseThrow(InvalidCredentialsException::new);
        return new LoginResponse(jwtService.generateToken(user));
    }
}
