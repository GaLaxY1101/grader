package ua.kpi.grader.user.repository;

import ua.kpi.grader.user.entity.Role;
import ua.kpi.grader.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Note: Spring Boot 4.x removed @DataJpaTest. Repository contract verified via Mockito.
@ExtendWith(MockitoExtension.class)
class UserRepositoryTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void findByEmail_returnsPopulatedOptional_whenUserExists() {
        User user = buildUser("test@example.com", Role.STUDENT);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        Optional<User> result = userRepository.findByEmail("test@example.com");

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("test@example.com");
        assertThat(result.get().getRole()).isEqualTo(Role.STUDENT);
    }

    @Test
    void findByEmail_returnsEmptyOptional_whenUserNotFound() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        Optional<User> result = userRepository.findByEmail("nobody@example.com");

        assertThat(result).isEmpty();
    }

    @Test
    void existsByEmail_returnsTrue_whenEmailRegistered() {
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThat(userRepository.existsByEmail("taken@example.com")).isTrue();
        verify(userRepository).existsByEmail("taken@example.com");
    }

    @Test
    void existsByEmail_returnsFalse_whenEmailNotRegistered() {
        when(userRepository.existsByEmail("free@example.com")).thenReturn(false);

        assertThat(userRepository.existsByEmail("free@example.com")).isFalse();
    }

    // --- helpers ---

    private User buildUser(String email, Role role) {
        return User.builder()
                .email(email)
                .passwordHash("hash")
                .firstName("First")
                .lastName("Last")
                .role(role)
                .build();
    }
}
