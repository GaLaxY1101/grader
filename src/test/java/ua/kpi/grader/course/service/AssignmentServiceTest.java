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
import ua.kpi.grader.course.dto.ProgrammingTaskDetails;
import ua.kpi.grader.course.dto.UpdateAssignmentRequest;
import ua.kpi.grader.course.entity.Assignment;
import ua.kpi.grader.course.entity.Course;
import ua.kpi.grader.course.entity.Language;
import ua.kpi.grader.course.entity.ProgrammingTask;
import ua.kpi.grader.course.entity.TestMode;
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
        CreateAssignmentRequest request = new CreateAssignmentRequest("HW1", null, 50, null, null);
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
        CreateAssignmentRequest request = new CreateAssignmentRequest("HW1", null, 50, null, null);
        when(courseRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assignmentService.createAssignment(99L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void createAssignment_throwsResourceNotFoundException_whenTeacherNotFound() {
        Course course = buildCourse(1L);
        CreateAssignmentRequest request = new CreateAssignmentRequest("HW1", null, 50, null, null);
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
        UpdateAssignmentRequest request = new UpdateAssignmentRequest("Updated HW", null, 80, null, null);
        when(assignmentRepository.findByIdAndIsActiveTrue(5L)).thenReturn(Optional.of(assignment));

        AssignmentResponse result = assignmentService.updateAssignment(5L, request);

        assertThat(result.title()).isEqualTo("Updated HW");
        assertThat(result.maxScore()).isEqualTo(80);
    }

    @Test
    void updateAssignment_throwsResourceNotFoundException_whenNotFound() {
        UpdateAssignmentRequest request = new UpdateAssignmentRequest("HW", null, 50, null, null);
        when(assignmentRepository.findByIdAndIsActiveTrue(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assignmentService.updateAssignment(99L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void updateAssignment_addsCodeCheck_whenIncomingProvidedAndNoExisting() {
        Course course = buildCourse(1L);
        Teacher teacher = buildTeacher(1L, 10L);
        Assignment assignment = buildAssignment(5L, course, teacher);
        assertThat(assignment.getProgrammingTask()).isNull();

        ProgrammingTaskDetails details = new ProgrammingTaskDetails(
                Language.CPP, TestMode.UNIT_TEST, null, "int solve(int)", "int main(){return 0;}");
        UpdateAssignmentRequest request = new UpdateAssignmentRequest("HW", null, 50, null, details);
        when(assignmentRepository.findByIdAndIsActiveTrue(5L)).thenReturn(Optional.of(assignment));

        AssignmentResponse result = assignmentService.updateAssignment(5L, request);

        assertThat(assignment.getProgrammingTask()).isNotNull();
        assertThat(assignment.getProgrammingTask().getLanguage()).isEqualTo(Language.CPP);
        assertThat(assignment.getProgrammingTask().getFunctionSignature()).isEqualTo("int solve(int)");
        assertThat(result.programmingTask()).isNotNull();
    }

    @Test
    void updateAssignment_removesCodeCheck_whenIncomingNullAndExistingPresent() {
        Course course = buildCourse(1L);
        Teacher teacher = buildTeacher(1L, 10L);
        Assignment assignment = buildAssignment(5L, course, teacher);
        ProgrammingTask existing = ProgrammingTask.builder()
                .language(Language.CPP)
                .testMode(TestMode.UNIT_TEST)
                .functionSignature("int solve(int)")
                .build();
        existing.setAssignment(assignment);
        assignment.setProgrammingTask(existing);

        UpdateAssignmentRequest request = new UpdateAssignmentRequest("HW", null, 50, null, null);
        when(assignmentRepository.findByIdAndIsActiveTrue(5L)).thenReturn(Optional.of(assignment));

        AssignmentResponse result = assignmentService.updateAssignment(5L, request);

        assertThat(assignment.getProgrammingTask()).isNull();
        assertThat(result.programmingTask()).isNull();
    }

    @Test
    void updateAssignment_addsCodeCheck_forPythonLanguage() {
        Course course = buildCourse(1L);
        Teacher teacher = buildTeacher(1L, 10L);
        Assignment assignment = buildAssignment(5L, course, teacher);

        ProgrammingTaskDetails details = new ProgrammingTaskDetails(
                Language.PYTHON, TestMode.UNIT_TEST, null,
                "def solve(x):\n    pass",
                "from solution import solve\n\ndef test_ok():\n    assert True\n");
        UpdateAssignmentRequest request = new UpdateAssignmentRequest("HW", null, 50, null, details);
        when(assignmentRepository.findByIdAndIsActiveTrue(5L)).thenReturn(Optional.of(assignment));

        AssignmentResponse result = assignmentService.updateAssignment(5L, request);

        assertThat(assignment.getProgrammingTask()).isNotNull();
        assertThat(assignment.getProgrammingTask().getLanguage()).isEqualTo(Language.PYTHON);
        assertThat(result.programmingTask()).isNotNull();
    }

    @Test
    void updateAssignment_leavesCodeCheckUntouched_whenBothNull() {
        Course course = buildCourse(1L);
        Teacher teacher = buildTeacher(1L, 10L);
        Assignment assignment = buildAssignment(5L, course, teacher);
        UpdateAssignmentRequest request = new UpdateAssignmentRequest("HW", null, 50, null, null);
        when(assignmentRepository.findByIdAndIsActiveTrue(5L)).thenReturn(Optional.of(assignment));

        AssignmentResponse result = assignmentService.updateAssignment(5L, request);

        assertThat(assignment.getProgrammingTask()).isNull();
        assertThat(result.programmingTask()).isNull();
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
