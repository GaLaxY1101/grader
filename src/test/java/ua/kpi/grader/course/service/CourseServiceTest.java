package ua.kpi.grader.course.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import ua.kpi.grader.common.exception.ResourceNotFoundException;
import ua.kpi.grader.course.dto.*;
import ua.kpi.grader.course.entity.Course;
import ua.kpi.grader.course.entity.CourseEnrollment;
import ua.kpi.grader.course.entity.CourseTeacher;
import ua.kpi.grader.course.repository.AssignmentRepository;
import ua.kpi.grader.course.repository.CourseEnrollmentRepository;
import ua.kpi.grader.course.repository.CourseRepository;
import ua.kpi.grader.course.repository.CourseTeacherRepository;
import ua.kpi.grader.user.entity.Role;
import ua.kpi.grader.user.entity.Student;
import ua.kpi.grader.user.entity.Teacher;
import ua.kpi.grader.user.entity.User;
import ua.kpi.grader.security.CurrentUser;
import ua.kpi.grader.user.repository.StudentRepository;
import ua.kpi.grader.user.repository.TeacherRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseEnrollmentRepository enrollmentRepository;

    @Mock
    private CourseTeacherRepository courseTeacherRepository;

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private TeacherRepository teacherRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private CurrentUser currentUser;

    @InjectMocks
    private CourseServiceImpl courseService;

    // --- findAllActive ---

    @Test
    void findAllActive_returnsActiveCourses() {
        Course course = buildCourse(1L, "Math");
        when(courseRepository.findAllByIsActiveTrue()).thenReturn(List.of(course));

        List<CourseResponse> result = courseService.findAllActive();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Math");
    }

    // --- findById ---

    @Test
    void findById_returnsCourseDetail_whenExists() {
        Course course = buildCourse(1L, "Physics");
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseTeacherRepository.findAllByCourseIdWithTeacherUser(1L)).thenReturn(List.of());
        when(enrollmentRepository.findAllByCourseIdWithStudentUser(1L)).thenReturn(List.of());
        when(assignmentRepository.findAllByCourseIdAndIsActiveTrue(1L)).thenReturn(List.of());

        CourseDetailResponse result = courseService.findById(1L);

        assertThat(result.name()).isEqualTo("Physics");
        assertThat(result.teachers()).isEmpty();
        assertThat(result.students()).isEmpty();
    }

    @Test
    void findById_throwsResourceNotFoundException_whenNotFound() {
        when(courseRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // --- createCourse ---

    @Test
    void createCourse_persistsAndReturnsCourse() {
        Teacher teacher = buildTeacher(1L, 10L);
        CreateCourseRequest request = new CreateCourseRequest("CS101", null, 2024, 1, null, null);
        Course saved = buildCourse(5L, "CS101");
        when(currentUser.getEmail()).thenReturn("teacher10@test.com");
        when(teacherRepository.findByUser_Email("teacher10@test.com")).thenReturn(Optional.of(teacher));
        when(courseRepository.save(any())).thenReturn(saved);

        CourseResponse result = courseService.createCourse(request);

        assertThat(result.name()).isEqualTo("CS101");
        verify(courseRepository).save(any(Course.class));
    }

    @Test
    void createCourse_throwsResourceNotFoundException_whenTeacherNotFound() {
        CreateCourseRequest request = new CreateCourseRequest("CS101", null, 2024, 1, null, null);
        when(currentUser.getEmail()).thenReturn("nobody@test.com");
        when(teacherRepository.findByUser_Email("nobody@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.createCourse(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("nobody@test.com");
    }

    // --- updateCourse ---

    @Test
    void updateCourse_updatesFields_whenExists() {
        Course course = buildCourse(1L, "Old Name");
        UpdateCourseRequest request = new UpdateCourseRequest("New Name", "desc", 2024, 2, null, null);
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

        CourseResponse result = courseService.updateCourse(1L, request);

        assertThat(result.name()).isEqualTo("New Name");
    }

    @Test
    void updateCourse_throwsResourceNotFoundException_whenNotFound() {
        UpdateCourseRequest request = new UpdateCourseRequest("Name", null, 2024, 1, null, null);
        when(courseRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.updateCourse(99L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // --- deactivateCourse ---

    @Test
    void deactivateCourse_setsIsActiveFalse() {
        Course course = buildCourse(1L, "CS101");
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

        courseService.deactivateCourse(1L);

        assertThat(course.isActive()).isFalse();
    }

    @Test
    void deactivateCourse_throwsResourceNotFoundException_whenNotFound() {
        when(courseRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.deactivateCourse(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // --- enrollStudent ---

    @Test
    void enrollStudent_createsEnrollment_whenBothExist() {
        Course course = buildCourse(1L, "CS101");
        Student student = buildStudent(2L, "student@test.com");
        CourseEnrollment saved = CourseEnrollment.builder().course(course).student(student).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(studentRepository.findById(2L)).thenReturn(Optional.of(student));
        when(enrollmentRepository.existsByCourseIdAndStudentId(1L, 2L)).thenReturn(false);
        when(enrollmentRepository.save(any())).thenReturn(saved);

        EnrolledStudentResponse result = courseService.enrollStudent(1L, 2L);

        assertThat(result.email()).isEqualTo("student@test.com");
        verify(enrollmentRepository).save(any(CourseEnrollment.class));
    }

    @Test
    void enrollStudent_throwsResourceNotFoundException_whenCourseNotFound() {
        when(courseRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.enrollStudent(99L, 2L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void enrollStudent_throwsResourceNotFoundException_whenStudentNotFound() {
        Course course = buildCourse(1L, "CS101");
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(studentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.enrollStudent(1L, 99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void enrollStudent_throwsIllegalStateException_whenAlreadyEnrolled() {
        Course course = buildCourse(1L, "CS101");
        Student student = buildStudent(2L, "student@test.com");
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(studentRepository.findById(2L)).thenReturn(Optional.of(student));
        when(enrollmentRepository.existsByCourseIdAndStudentId(1L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> courseService.enrollStudent(1L, 2L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already enrolled");
    }

    // --- unenrollStudent ---

    @Test
    void unenrollStudent_dropsEnrollment_whenEnrolled() {
        Course course = buildCourse(1L, "CS101");
        Student student = buildStudent(2L, "student@test.com");
        CourseEnrollment enrollment = CourseEnrollment.builder().course(course).student(student).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.findByCourseIdAndStudentId(1L, 2L)).thenReturn(Optional.of(enrollment));

        courseService.unenrollStudent(1L, 2L);

        assertThat(enrollment.getStatus()).isEqualTo("DROPPED");
    }

    @Test
    void unenrollStudent_throwsResourceNotFoundException_whenNotEnrolled() {
        Course course = buildCourse(1L, "CS101");
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.findByCourseIdAndStudentId(1L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.unenrollStudent(1L, 99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // --- addTeacher ---

    @Test
    void addTeacher_createsCourseTeacher_whenNotAlreadyAssigned() {
        Course course = buildCourse(1L, "CS101");
        Teacher teacher = buildTeacher(3L, 30L);
        CourseTeacher saved = CourseTeacher.builder().course(course).teacher(teacher).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(teacherRepository.findById(3L)).thenReturn(Optional.of(teacher));
        when(courseTeacherRepository.existsByCourseIdAndTeacherId(1L, 3L)).thenReturn(false);
        when(courseTeacherRepository.save(any())).thenReturn(saved);

        CourseTeacherResponse result = courseService.addTeacher(1L, 3L);

        assertThat(result.email()).isEqualTo("teacher30@test.com");
        verify(courseTeacherRepository).save(any(CourseTeacher.class));
    }

    @Test
    void addTeacher_throwsIllegalStateException_whenAlreadyAssigned() {
        Course course = buildCourse(1L, "CS101");
        Teacher teacher = buildTeacher(3L, 30L);
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(teacherRepository.findById(3L)).thenReturn(Optional.of(teacher));
        when(courseTeacherRepository.existsByCourseIdAndTeacherId(1L, 3L)).thenReturn(true);

        assertThatThrownBy(() -> courseService.addTeacher(1L, 3L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already assigned");
    }

    // --- findStudents ---

    @Test
    void findStudents_returnsActiveStudents_whenCourseExists() {
        Course course = buildCourse(1L, "CS101");
        Student student = buildStudent(2L, "bob@test.com");
        CourseEnrollment enrollment = CourseEnrollment.builder().course(course).student(student).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.findAllByCourseIdWithStudentUser(1L)).thenReturn(List.of(enrollment));

        List<EnrolledStudentResponse> result = courseService.findStudents(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).email()).isEqualTo("bob@test.com");
    }

    @Test
    void findStudents_throwsResourceNotFoundException_whenCourseNotFound() {
        when(courseRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.findStudents(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // --- helpers ---

    private Course buildCourse(Long id, String name) {
        Teacher teacher = buildTeacher(1L, 10L);
        Course course = Course.builder()
                .name(name)
                .academicYear(2024)
                .semester(1)
                .createdBy(teacher)
                .build();
        ReflectionTestUtils.setField(course, "id", id);
        ReflectionTestUtils.setField(course, "isActive", true);
        return course;
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

    private Student buildStudent(Long studentId, String email) {
        User user = User.builder()
                .email(email)
                .firstName("Student")
                .lastName("Test")
                .role(Role.STUDENT)
                .build();
        user.setId(studentId + 100L);
        Student student = Student.builder().user(user).build();
        student.setId(studentId);
        return student;
    }
}
