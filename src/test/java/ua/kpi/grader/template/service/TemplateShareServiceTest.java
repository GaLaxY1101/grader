package ua.kpi.grader.template.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import ua.kpi.grader.common.exception.ResourceNotFoundException;
import ua.kpi.grader.template.dto.CreateTemplateShareRequest;
import ua.kpi.grader.template.dto.TemplateShareResponse;
import ua.kpi.grader.template.entity.CourseTemplate;
import ua.kpi.grader.template.entity.TemplateShare;
import ua.kpi.grader.template.repository.TemplateShareRepository;
import ua.kpi.grader.user.entity.Teacher;
import ua.kpi.grader.user.entity.User;
import ua.kpi.grader.user.repository.TeacherRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemplateShareServiceTest {

    @Mock
    private TemplateShareRepository shareRepository;

    @Mock
    private TeacherRepository teacherRepository;

    @Mock
    private TemplateAccessService access;

    @InjectMocks
    private TemplateShareServiceImpl service;

    @Test
    void shareTemplate_persistsAndReturnsShare() {
        Teacher owner = teacher(7L);
        Teacher target = teacher(8L);
        CourseTemplate template = template(1L, owner);
        when(access.requireEdit(1L)).thenReturn(template);
        when(teacherRepository.findById(8L)).thenReturn(Optional.of(target));
        when(shareRepository.existsByTemplateIdAndSharedWithTeacherId(1L, 8L)).thenReturn(false);
        when(access.currentTeacher()).thenReturn(owner);
        when(shareRepository.save(any())).thenAnswer(inv -> {
            TemplateShare arg = inv.getArgument(0);
            ReflectionTestUtils.setField(arg, "id", 500L);
            return arg;
        });

        TemplateShareResponse result = service.shareTemplate(1L,
                new CreateTemplateShareRequest(8L));

        assertThat(result.id()).isEqualTo(500L);
        assertThat(result.teacherId()).isEqualTo(8L);
    }

    @Test
    void shareTemplate_throwsResourceNotFound_whenTargetTeacherMissing() {
        Teacher owner = teacher(7L);
        CourseTemplate template = template(1L, owner);
        when(access.requireEdit(1L)).thenReturn(template);
        when(teacherRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.shareTemplate(1L, new CreateTemplateShareRequest(99L)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void shareTemplate_throwsIllegalState_whenTargetIsOwner() {
        Teacher owner = teacher(7L);
        CourseTemplate template = template(1L, owner);
        when(access.requireEdit(1L)).thenReturn(template);
        when(teacherRepository.findById(7L)).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> service.shareTemplate(1L, new CreateTemplateShareRequest(7L)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shareTemplate_throwsIllegalState_whenAlreadyShared() {
        Teacher owner = teacher(7L);
        Teacher target = teacher(8L);
        CourseTemplate template = template(1L, owner);
        when(access.requireEdit(1L)).thenReturn(template);
        when(teacherRepository.findById(8L)).thenReturn(Optional.of(target));
        when(shareRepository.existsByTemplateIdAndSharedWithTeacherId(1L, 8L)).thenReturn(true);

        assertThatThrownBy(() -> service.shareTemplate(1L, new CreateTemplateShareRequest(8L)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unshareTemplate_deletesExistingShare() {
        Teacher owner = teacher(7L);
        Teacher target = teacher(8L);
        CourseTemplate template = template(1L, owner);
        TemplateShare share = TemplateShare.builder()
                .template(template).sharedWithTeacher(target).sharedByTeacher(owner).build();
        when(access.requireEdit(1L)).thenReturn(template);
        when(shareRepository.findByTemplateIdAndSharedWithTeacherId(1L, 8L))
                .thenReturn(Optional.of(share));

        service.unshareTemplate(1L, 8L);

        verify(shareRepository).delete(share);
    }

    @Test
    void unshareTemplate_throwsResourceNotFound_whenShareMissing() {
        Teacher owner = teacher(7L);
        CourseTemplate template = template(1L, owner);
        when(access.requireEdit(1L)).thenReturn(template);
        when(shareRepository.findByTemplateIdAndSharedWithTeacherId(1L, 8L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unshareTemplate(1L, 8L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private Teacher teacher(Long id) {
        User user = User.builder().email("t" + id + "@test.com").firstName("F").lastName("L").build();
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
