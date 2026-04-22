package ua.kpi.grader.course.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import ua.kpi.grader.common.exception.ResourceNotFoundException;
import ua.kpi.grader.course.dto.AssignmentResponse;
import ua.kpi.grader.course.dto.CreateAssignmentRequest;
import ua.kpi.grader.course.dto.UpdateAssignmentRequest;
import ua.kpi.grader.course.entity.Assignment;
import ua.kpi.grader.course.entity.Course;
import ua.kpi.grader.course.repository.AssignmentRepository;
import ua.kpi.grader.course.repository.CourseRepository;
import ua.kpi.grader.user.entity.Role;
import ua.kpi.grader.user.entity.Teacher;
import ua.kpi.grader.user.entity.User;
import ua.kpi.grader.security.CurrentUser;
import ua.kpi.grader.user.repository.TeacherRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssignmentServiceTest {

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private TeacherRepository teacherRepository;

    @Mock
    private CurrentUser currentUser;

    @InjectMocks
    private AssignmentServiceImpl assignmentService;

    // --- findAllByCourse ---

    @Test
    void findAllByCourse_returnsActiveAssignments_whenCourseExists() {
        Course course = buildCourse(1L);
        Teacher teacher = buildTeacher(1L, 10L);
        Assignment assignment = buildAssignment(5L, course, teacher);
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(assignmentRepository.findAllByCourseIdAndIsActiveTrue(1L)).thenReturn(List.of(assignment));

        List<AssignmentResponse> result = assignmentService.findAllByCourse(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("Test Assignment");
    }

    @Test
    void findAllByCourse_throwsResourceNotFoundException_whenCourseNotFound() {
        when(courseRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assignmentService.findAllByCourse(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // --- findById ---

    @Test
    void findById_returnsAssignment_whenExists() {
        Course course = buildCourse(1L);
        Teacher teacher = buildTeacher(1L, 10L);
        Assignment assignment = buildAssignment(5L, course, teacher);
        when(assignmentRepository.findByIdAndIsActiveTrue(5L)).thenReturn(Optional.of(assignment));

        AssignmentResponse result = assignmentService.findById(5L);

        assertThat(result.title()).isEqualTo("Test Assignment");
    }

    @Test
    void findById_throwsResourceNotFoundException_whenNotFound() {
        when(assignmentRepository.findByIdAndIsActiveTrue(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assignmentService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // --- createAssignment ---

    @Test
    void createAssignment_persistsAndReturnsAssignment() {
        Course course = buildCourse(1L);
        Teacher teacher = buildTeacher(1L, 10L);
        CreateAssignmentRequest request = new CreateAssignmentRequest("HW1", null, 50, null, null, null);
        Assignment saved = buildAssignment(7L, course, teacher);
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(currentUser.getEmail()).thenReturn("teacher10@test.com");
        when(teacherRepository.findByUser_Email("teacher10@test.com")).thenReturn(Optional.of(teacher));
        when(assignmentRepository.save(any())).thenReturn(saved);

        AssignmentResponse result = assignmentService.createAssignment(1L, request);

        assertThat(result.title()).isEqualTo("Test Assignment");
        verify(assignmentRepository).save(any(Assignment.class));
    }

    @Test
    void createAssignment_throwsResourceNotFoundException_whenCourseNotFound() {
        CreateAssignmentRequest request = new CreateAssignmentRequest("HW1", null, 50, null, null, null);
        when(courseRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assignmentService.createAssignment(99L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void createAssignment_throwsResourceNotFoundException_whenTeacherNotFound() {
        Course course = buildCourse(1L);
        CreateAssignmentRequest request = new CreateAssignmentRequest("HW1", null, 50, null, null, null);
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(currentUser.getEmail()).thenReturn("nobody@test.com");
        when(teacherRepository.findByUser_Email("nobody@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assignmentService.createAssignment(1L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("nobody@test.com");
    }

    // --- updateAssignment ---

    @Test
    void updateAssignment_updatesFields_whenExists() {
        Course course = buildCourse(1L);
        Teacher teacher = buildTeacher(1L, 10L);
        Assignment assignment = buildAssignment(5L, course, teacher);
        UpdateAssignmentRequest request = new UpdateAssignmentRequest("Updated HW", null, 80, null);
        when(assignmentRepository.findByIdAndIsActiveTrue(5L)).thenReturn(Optional.of(assignment));

        AssignmentResponse result = assignmentService.updateAssignment(5L, request);

        assertThat(result.title()).isEqualTo("Updated HW");
        assertThat(result.maxScore()).isEqualTo(80);
    }

    @Test
    void updateAssignment_throwsResourceNotFoundException_whenNotFound() {
        UpdateAssignmentRequest request = new UpdateAssignmentRequest("HW", null, 50, null);
        when(assignmentRepository.findByIdAndIsActiveTrue(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assignmentService.updateAssignment(99L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // --- deactivateAssignment ---

    @Test
    void deactivateAssignment_setsIsActiveFalse() {
        Course course = buildCourse(1L);
        Teacher teacher = buildTeacher(1L, 10L);
        Assignment assignment = buildAssignment(5L, course, teacher);
        when(assignmentRepository.findByIdAndIsActiveTrue(5L)).thenReturn(Optional.of(assignment));

        assignmentService.deactivateAssignment(5L);

        assertThat(assignment.isActive()).isFalse();
    }

    @Test
    void deactivateAssignment_throwsResourceNotFoundException_whenNotFound() {
        when(assignmentRepository.findByIdAndIsActiveTrue(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assignmentService.deactivateAssignment(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // --- helpers ---

    private Course buildCourse(Long id) {
        Teacher teacher = buildTeacher(1L, 10L);
        Course course = Course.builder()
                .name("Test Course")
                .academicYear(2024)
                .semester(1)
                .createdBy(teacher)
                .build();
        ReflectionTestUtils.setField(course, "id", id);
        ReflectionTestUtils.setField(course, "isActive", true);
        return course;
    }

    private Assignment buildAssignment(Long id, Course course, Teacher teacher) {
        Assignment assignment = Assignment.builder()
                .course(course)
                .title("Test Assignment")
                .maxScore(100)
                .createdBy(teacher)
                .build();
        ReflectionTestUtils.setField(assignment, "id", id);
        ReflectionTestUtils.setField(assignment, "isActive", true);
        return assignment;
    }

    private Teacher buildTeacher(Long teacherId, Long userId) {
        User user = User.builder()
                .email("teacher" + userId + "@test.com")
                .firstName("Teacher")
                .lastName("Test")
                .role(Role.TEACHER)
                .build();
        user.setId(userId);
        Teacher teacher = Teacher.builder().user(user).build();
        teacher.setId(teacherId);
        return teacher;
    }
}
