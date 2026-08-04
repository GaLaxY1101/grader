package ua.kpi.grader.template.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import ua.kpi.grader.common.dto.PageResponse;
import ua.kpi.grader.security.CurrentUser;
import ua.kpi.grader.template.dto.CourseTemplateDetailResponse;
import ua.kpi.grader.template.dto.CourseTemplateResponse;
import ua.kpi.grader.template.dto.CreateCourseTemplateRequest;
import ua.kpi.grader.template.dto.UpdateCourseTemplateRequest;
import ua.kpi.grader.template.entity.CourseTemplate;
import ua.kpi.grader.template.entity.TemplateAssignment;
import ua.kpi.grader.template.mapper.TemplateContentMapper;
import ua.kpi.grader.template.repository.CourseTemplateRepository;
import ua.kpi.grader.template.repository.TemplateAssignmentRepository;
import ua.kpi.grader.user.entity.Teacher;
import ua.kpi.grader.user.entity.User;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseTemplateServiceTest {

    @Mock
    private CourseTemplateRepository templateRepository;

    @Mock
    private TemplateAssignmentRepository assignmentRepository;

    @Mock
    private TemplateAccessService access;

    @Mock
    private TemplateContentMapper mapper;

    @Mock
    private CurrentUser currentUser;

    @InjectMocks
    private CourseTemplateServiceImpl service;

    @Test
    void findVisible_asTeacher_usesVisibleTo() {
        Teacher teacher = teacher(7L);
        CourseTemplate t = template(1L, teacher);
        Pageable pageable = PageRequest.of(0, 12);
        Page<CourseTemplate> page = new PageImpl<>(List.of(t), pageable, 1);
        when(currentUser.hasRole("ADMIN")).thenReturn(false);
        when(access.currentTeacher()).thenReturn(teacher);
        when(templateRepository.findVisibleTo(eq(7L), eq("math"), eq(pageable))).thenReturn(page);

        PageResponse<CourseTemplateResponse> result = service.findVisible("  Math  ", pageable);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).id()).isEqualTo(1L);
    }

    @Test
    void findVisible_asAdmin_usesFindAllFiltered() {
        Teacher teacher = teacher(7L);
        CourseTemplate t = template(1L, teacher);
        Pageable pageable = PageRequest.of(0, 12);
        Page<CourseTemplate> page = new PageImpl<>(List.of(t), pageable, 1);
        when(currentUser.hasRole("ADMIN")).thenReturn(true);
        when(templateRepository.findAllFiltered(eq(null), eq(pageable))).thenReturn(page);

        PageResponse<CourseTemplateResponse> result = service.findVisible(null, pageable);

        assertThat(result.content()).hasSize(1);
    }

    @Test
    void findById_returnsDetailWithAssignments() {
        Teacher teacher = teacher(7L);
        CourseTemplate t = template(1L, teacher);
        TemplateAssignment a = TemplateAssignment.builder().template(t).title("A").maxScore(100).build();
        ReflectionTestUtils.setField(a, "id", 100L);
        when(access.requireView(1L)).thenReturn(t);
        when(assignmentRepository.findAllByTemplateId(1L)).thenReturn(List.of(a));

        CourseTemplateDetailResponse result = service.findById(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.assignments()).hasSize(1);
        assertThat(result.assignments().get(0).title()).isEqualTo("A");
    }

    @Test
    void createTemplate_persistsWithCurrentTeacherAsOwner() {
        Teacher teacher = teacher(7L);
        CreateCourseTemplateRequest request = new CreateCourseTemplateRequest("New", "desc");
        CourseTemplate saved = template(5L, teacher);
        when(access.currentTeacher()).thenReturn(teacher);
        when(templateRepository.save(any())).thenReturn(saved);

        CourseTemplateResponse result = service.createTemplate(request);

        assertThat(result.id()).isEqualTo(5L);
        ArgumentCaptor<CourseTemplate> captor = ArgumentCaptor.forClass(CourseTemplate.class);
        verify(templateRepository).save(captor.capture());
        assertThat(captor.getValue().getOwner()).isSameAs(teacher);
        assertThat(captor.getValue().getName()).isEqualTo("New");
    }

    @Test
    void updateTemplate_requiresEdit_thenUpdates() {
        Teacher teacher = teacher(7L);
        CourseTemplate t = template(1L, teacher);
        when(access.requireEdit(1L)).thenReturn(t);

        CourseTemplateResponse result = service.updateTemplate(1L,
                new UpdateCourseTemplateRequest("Renamed", "new desc"));

        assertThat(result.name()).isEqualTo("Renamed");
    }

    @Test
    void deleteTemplate_requiresEdit_thenDeletes() {
        Teacher teacher = teacher(7L);
        CourseTemplate t = template(1L, teacher);
        when(access.requireEdit(1L)).thenReturn(t);

        service.deleteTemplate(1L);

        verify(templateRepository).delete(t);
    }

    @Test
    void copyTemplate_clonesAllAssignmentsUnderNewOwner() {
        Teacher owner = teacher(7L);
        Teacher copier = teacher(8L);
        CourseTemplate source = template(1L, owner);
        source = CourseTemplate.builder().name("Src").description("d").owner(owner).build();
        ReflectionTestUtils.setField(source, "id", 1L);

        TemplateAssignment a1 = TemplateAssignment.builder().template(source).title("A1").maxScore(50).build();
        TemplateAssignment a2 = TemplateAssignment.builder().template(source).title("A2").maxScore(60).build();

        when(access.requireView(1L)).thenReturn(source);
        when(access.currentTeacher()).thenReturn(copier);
        when(templateRepository.save(any())).thenAnswer(inv -> {
            CourseTemplate arg = inv.getArgument(0);
            ReflectionTestUtils.setField(arg, "id", 42L);
            return arg;
        });
        when(assignmentRepository.findAllByTemplateId(1L)).thenReturn(List.of(a1, a2));
        when(mapper.cloneAssignment(any(), any())).thenAnswer(inv ->
                TemplateAssignment.builder().template(inv.getArgument(1)).title("clone").maxScore(1).build());

        CourseTemplateResponse result = service.copyTemplate(1L);

        assertThat(result.id()).isEqualTo(42L);
        ArgumentCaptor<CourseTemplate> templateCaptor = ArgumentCaptor.forClass(CourseTemplate.class);
        verify(templateRepository).save(templateCaptor.capture());
        assertThat(templateCaptor.getValue().getOwner()).isSameAs(copier);
        assertThat(templateCaptor.getValue().getName()).isEqualTo("Src (copy)");
        verify(assignmentRepository, times(2)).save(any());
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
