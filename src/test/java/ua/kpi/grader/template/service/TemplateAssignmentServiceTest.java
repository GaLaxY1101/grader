package ua.kpi.grader.template.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import ua.kpi.grader.common.exception.ResourceNotFoundException;
import ua.kpi.grader.template.dto.CreateTemplateAssignmentRequest;
import ua.kpi.grader.template.dto.TemplateAssignmentResponse;
import ua.kpi.grader.template.dto.UpdateTemplateAssignmentRequest;
import ua.kpi.grader.template.entity.CourseTemplate;
import ua.kpi.grader.template.entity.TemplateAssignment;
import ua.kpi.grader.template.mapper.TemplateContentMapper;
import ua.kpi.grader.template.repository.TemplateAssignmentRepository;
import ua.kpi.grader.user.entity.Teacher;
import ua.kpi.grader.user.entity.User;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemplateAssignmentServiceTest {

    @Mock
    private TemplateAssignmentRepository assignmentRepository;

    @Mock
    private TemplateAccessService access;

    @Mock
    private TemplateContentMapper mapper;

    @InjectMocks
    private TemplateAssignmentServiceImpl service;

    @Test
    void findAllByTemplate_requiresView_returnsAssignments() {
        Teacher owner = teacher(7L);
        CourseTemplate template = template(1L, owner);
        TemplateAssignment a = TemplateAssignment.builder().template(template).title("A").maxScore(100).build();
        ReflectionTestUtils.setField(a, "id", 10L);
        when(access.requireView(1L)).thenReturn(template);
        when(assignmentRepository.findAllByTemplateId(1L)).thenReturn(List.of(a));

        List<TemplateAssignmentResponse> result = service.findAllByTemplate(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("A");
    }

    @Test
    void createAssignment_requiresEdit_persistsWithDefaultMaxScore() {
        Teacher owner = teacher(7L);
        CourseTemplate template = template(1L, owner);
        when(access.requireEdit(1L)).thenReturn(template);
        when(assignmentRepository.save(any())).thenAnswer(inv -> {
            TemplateAssignment arg = inv.getArgument(0);
            ReflectionTestUtils.setField(arg, "id", 55L);
            return arg;
        });

        TemplateAssignmentResponse result = service.createAssignment(1L,
                new CreateTemplateAssignmentRequest("Title", "d", null, null));

        assertThat(result.id()).isEqualTo(55L);
        assertThat(result.maxScore()).isEqualTo(100);
    }

    @Test
    void updateAssignment_throwsResourceNotFound_whenMissing() {
        when(assignmentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateAssignment(99L,
                new UpdateTemplateAssignmentRequest("t", null, 100, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateAssignment_removesProgrammingTask_whenIncomingNullAndExistingPresent() {
        Teacher owner = teacher(7L);
        CourseTemplate template = template(1L, owner);
        TemplateAssignment a = TemplateAssignment.builder().template(template).title("A").maxScore(100).build();
        ReflectionTestUtils.setField(a, "id", 10L);
        a.setProgrammingTask(ua.kpi.grader.template.entity.TemplateProgrammingTask.builder()
                .language(ua.kpi.grader.course.entity.Language.CPP)
                .testMode(ua.kpi.grader.course.entity.TestMode.UNIT_TEST)
                .build());
        when(assignmentRepository.findById(10L)).thenReturn(Optional.of(a));
        when(access.requireEdit(1L)).thenReturn(template);

        service.updateAssignment(10L,
                new UpdateTemplateAssignmentRequest("Renamed", null, 100, null));

        assertThat(a.getProgrammingTask()).isNull();
    }

    @Test
    void deleteAssignment_requiresEditOnParent_thenDeletes() {
        Teacher owner = teacher(7L);
        CourseTemplate template = template(1L, owner);
        TemplateAssignment a = TemplateAssignment.builder().template(template).title("A").maxScore(100).build();
        ReflectionTestUtils.setField(a, "id", 10L);
        when(assignmentRepository.findById(10L)).thenReturn(Optional.of(a));
        when(access.requireEdit(1L)).thenReturn(template);

        service.deleteAssignment(10L);

        verify(assignmentRepository).delete(a);
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
