package ua.kpi.grader.user.service;

import ua.kpi.grader.common.exception.ResourceNotFoundException;
import ua.kpi.grader.user.entity.Role;
import ua.kpi.grader.user.entity.User;
import ua.kpi.grader.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    // --- findByEmail ---

    @Test
    void findByEmail_returnsUser_whenEmailExists() {
        User user = buildUser("alice@example.com", Role.STUDENT);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        User result = userService.findByEmail("alice@example.com");

        assertThat(result.getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void findByEmail_throwsResourceNotFoundException_whenEmailNotFound() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findByEmail("ghost@example.com"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("ghost@example.com");
    }

    // --- findById ---

    @Test
    void findById_returnsUser_whenIdExists() {
        User user = buildUser("bob@example.com", Role.TEACHER);
        user.setId(42L);
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));

        User result = userService.findById(42L);

        assertThat(result.getId()).isEqualTo(42L);
    }

    @Test
    void findById_throwsResourceNotFoundException_whenIdNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // --- helpers ---

    private User buildUser(String email, Role role) {
        return User.builder()
                .email(email)
                .passwordHash("hash")
                .firstName("Test")
                .lastName("User")
                .role(role)
                .build();
    }
}
