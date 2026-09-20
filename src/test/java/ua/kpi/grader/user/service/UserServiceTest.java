package ua.kpi.grader.user.service;

import ua.kpi.grader.common.dto.PageResponse;
import ua.kpi.grader.common.exception.ResourceNotFoundException;
import ua.kpi.grader.keycloak.KeycloakAdminClient;
import ua.kpi.grader.user.dto.CreateUserRequest;
import ua.kpi.grader.user.dto.UserResponse;
import ua.kpi.grader.user.entity.Role;
import ua.kpi.grader.user.entity.User;
import ua.kpi.grader.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private KeycloakAdminClient keycloakAdminClient;

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

    // --- findAll ---

    @Test
    void findAll_paginatesAndMapsResults() {
        User alice = buildUser("alice@example.com", Role.STUDENT);
        alice.setId(1L);
        when(userRepository.search(isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(alice)));

        PageResponse<UserResponse> page =
                userService.findAll(null, PageRequest.of(0, 20));

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).email()).isEqualTo("alice@example.com");
        assertThat(page.totalElements()).isEqualTo(1);
    }

    @Test
    void findAll_lowercasesAndTrimsQuery_beforePassingToRepo() {
        when(userRepository.search(eq("bob"), any())).thenReturn(new PageImpl<>(List.of()));

        userService.findAll("  BOB  ", PageRequest.of(0, 20));

        verify(userRepository).search(eq("bob"), any());
    }

    @Test
    void findAll_treatsBlankQueryAsNull() {
        when(userRepository.search(isNull(), any())).thenReturn(new PageImpl<>(List.of()));

        userService.findAll("   ", PageRequest.of(0, 20));

        verify(userRepository).search(isNull(), any());
    }

    // --- findAllEmails ---

    @Test
    void findAllEmails_delegatesToRepository() {
        when(userRepository.findAllEmails())
                .thenReturn(List.of("a@example.com", "b@example.com"));

        assertThat(userService.findAllEmails())
                .containsExactly("a@example.com", "b@example.com");
    }

    // --- createUser ---

    @Test
    void createUser_persistsKeycloakUuid_returnedFromAdminClient() {
        String kcUuid = "11111111-2222-3333-4444-555555555555";
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(keycloakAdminClient.createUser("new@example.com", "New", "User"))
                .thenReturn(kcUuid);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.createUser(new CreateUserRequest(
                "new@example.com", "New", "User", null, null, Role.STUDENT));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getKeycloakId()).isEqualTo(kcUuid);
    }

    // --- helpers ---

    private User buildUser(String email, Role role) {
        return User.builder()
                .email(email)
                .firstName("Test")
                .lastName("User")
                .role(role)
                .build();
    }
}
