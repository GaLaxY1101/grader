package ua.kpi.grader.template.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import ua.kpi.grader.common.exception.ResourceNotFoundException;
import ua.kpi.grader.security.CurrentUser;
import ua.kpi.grader.template.entity.CourseTemplate;
import ua.kpi.grader.template.repository.CourseTemplateRepository;
import ua.kpi.grader.template.repository.TemplateShareRepository;
import ua.kpi.grader.user.entity.Teacher;
import ua.kpi.grader.user.entity.User;
import ua.kpi.grader.user.repository.TeacherRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemplateAccessServiceTest {

    @Mock
    private CourseTemplateRepository templateRepository;

    @Mock
    private TemplateShareRepository shareRepository;

    @Mock
    private TeacherRepository teacherRepository;

    @Mock
    private CurrentUser currentUser;

    @InjectMocks
    private TemplateAccessService access;

    @Test
    void requireView_returnsTemplate_whenOwner() {
        Teacher owner = teacher(7L, "owner@test.com");
        CourseTemplate template = template(1L, owner);
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(currentUser.hasRole("ADMIN")).thenReturn(false);
        when(currentUser.getEmail()).thenReturn("owner@test.com");
        when(teacherRepository.findByUser_Email("owner@test.com")).thenReturn(Optional.of(owner));

        assertThat(access.requireView(1L)).isSameAs(template);
    }

    @Test
    void requireView_returnsTemplate_whenSharedWith() {
        Teacher owner = teacher(7L, "owner@test.com");
        Teacher other = teacher(8L, "other@test.com");
        CourseTemplate template = template(1L, owner);
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(currentUser.hasRole("ADMIN")).thenReturn(false);
        when(currentUser.getEmail()).thenReturn("other@test.com");
        when(teacherRepository.findByUser_Email("other@test.com")).thenReturn(Optional.of(other));
        when(shareRepository.existsByTemplateIdAndSharedWithTeacherId(1L, 8L)).thenReturn(true);

        assertThat(access.requireView(1L)).isSameAs(template);
    }

    @Test
    void requireView_returnsTemplate_whenAdmin() {
        Teacher owner = teacher(7L, "owner@test.com");
        CourseTemplate template = template(1L, owner);
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(currentUser.hasRole("ADMIN")).thenReturn(true);

        assertThat(access.requireView(1L)).isSameAs(template);
    }

    @Test
    void requireView_throwsAccessDenied_whenNeitherOwnerNorShared() {
        Teacher owner = teacher(7L, "owner@test.com");
        Teacher other = teacher(8L, "other@test.com");
        CourseTemplate template = template(1L, owner);
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(currentUser.hasRole("ADMIN")).thenReturn(false);
        when(currentUser.getEmail()).thenReturn("other@test.com");
        when(teacherRepository.findByUser_Email("other@test.com")).thenReturn(Optional.of(other));
        when(shareRepository.existsByTemplateIdAndSharedWithTeacherId(1L, 8L)).thenReturn(false);

        assertThatThrownBy(() -> access.requireView(1L)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireView_throwsResourceNotFound_whenTemplateMissing() {
        when(templateRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> access.requireView(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void requireEdit_throwsAccessDenied_whenNotOwnerEvenIfShared() {
        Teacher owner = teacher(7L, "owner@test.com");
        Teacher other = teacher(8L, "other@test.com");
        CourseTemplate template = template(1L, owner);
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(currentUser.hasRole("ADMIN")).thenReturn(false);
        when(currentUser.getEmail()).thenReturn("other@test.com");
        when(teacherRepository.findByUser_Email("other@test.com")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> access.requireEdit(1L)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireEdit_returnsTemplate_whenAdmin() {
        Teacher owner = teacher(7L, "owner@test.com");
        CourseTemplate template = template(1L, owner);
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(currentUser.hasRole("ADMIN")).thenReturn(true);

        assertThat(access.requireEdit(1L)).isSameAs(template);
    }

    private Teacher teacher(Long id, String email) {
        User user = User.builder().email(email).firstName("F").lastName("L").build();
        ReflectionTestUtils.setField(user, "id", id);
        Teacher t = Teacher.builder().user(user).build();
        t.setId(id);
        return t;
    }

    private CourseTemplate template(Long id, Teacher owner) {
        CourseTemplate t = CourseTemplate.builder().name("T").owner(owner).build();
        ReflectionTestUtils.setField(t, "id", id);
        return t;
    }
}
