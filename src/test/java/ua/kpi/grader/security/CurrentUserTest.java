package ua.kpi.grader.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentUserTest {

    private final CurrentUser currentUser = new CurrentUser();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @CsvSource({"STUDENT, false", "TEACHER, true", "ADMIN, true"})
    void isStaff_isTrueOnlyForTeacherAndAdmin(String role, boolean expected) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("user", null, "ROLE_" + role));

        assertThat(currentUser.isStaff()).isEqualTo(expected);
    }
}
