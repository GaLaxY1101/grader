package ua.kpi.grader.submission.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import ua.kpi.grader.common.exception.ResourceNotFoundException;
import ua.kpi.grader.course.entity.Assignment;
import ua.kpi.grader.course.entity.Course;
import ua.kpi.grader.course.repository.AssignmentRepository;
import ua.kpi.grader.security.CurrentUser;
import ua.kpi.grader.submission.dto.CreateSubmissionRequest;
import ua.kpi.grader.submission.dto.SubmissionResponse;
import ua.kpi.grader.submission.dto.SubmissionStatusResponse;
import ua.kpi.grader.submission.entity.Submission;
import ua.kpi.grader.submission.entity.SubmissionStatus;
import ua.kpi.grader.submission.repository.SubmissionRepository;
import ua.kpi.grader.user.entity.Role;
import ua.kpi.grader.user.entity.Student;
import ua.kpi.grader.user.entity.Teacher;
import ua.kpi.grader.user.entity.User;
import ua.kpi.grader.user.repository.StudentRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmissionServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private CurrentUser currentUser;

    @InjectMocks
    private SubmissionServiceImpl submissionService;

    private static final String STUDENT_EMAIL = "student@test.com";

    // --- createSubmission ---

    @Test
    void createSubmission_happyPath_returnsNewSubmission() {
        Assignment assignment = buildAssignment(1L);
        Student student = buildStudent(1L, STUDENT_EMAIL);
        when(currentUser.getEmail()).thenReturn(STUDENT_EMAIL);
        when(assignmentRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(assignment));
        when(studentRepository.findByUser_Email(STUDENT_EMAIL)).thenReturn(Optional.of(student));
        Submission saved = buildSubmission(1L, assignment, student);
        when(submissionRepository.save(any(Submission.class))).thenReturn(saved);

        SubmissionResponse response = submissionService.createSubmission(1L,
                new CreateSubmissionRequest("public class Main {}"));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(SubmissionStatus.PENDING);
        assertThat(response.studentEmail()).isEqualTo(STUDENT_EMAIL);
        verify(submissionRepository).save(any(Submission.class));
    }

    @Test
    void createSubmission_assignmentNotFound_throwsResourceNotFoundException() {
        when(assignmentRepository.findByIdAndIsActiveTrue(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> submissionService.createSubmission(99L,
                new CreateSubmissionRequest("code")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void createSubmission_studentNotFound_throwsResourceNotFoundException() {
        when(currentUser.getEmail()).thenReturn(STUDENT_EMAIL);
        when(assignmentRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(buildAssignment(1L)));
        when(studentRepository.findByUser_Email(STUDENT_EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> submissionService.createSubmission(1L,
                new CreateSubmissionRequest("code")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(STUDENT_EMAIL);
    }

    // --- findById ---

    @Test
    void findById_happyPath_returnsSubmission() {
        Student student = buildStudent(1L, STUDENT_EMAIL);
        Submission submission = buildSubmission(1L, buildAssignment(1L), student);
        when(currentUser.getEmail()).thenReturn(STUDENT_EMAIL);
        when(currentUser.hasRole("STUDENT")).thenReturn(true);
        when(submissionRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(submission));

        SubmissionResponse response = submissionService.findById(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.studentEmail()).isEqualTo(STUDENT_EMAIL);
    }

    @Test
    void findById_notFound_throwsResourceNotFoundException() {
        when(submissionRepository.findByIdWithDetails(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> submissionService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void findById_studentAccessingOthersSubmission_throwsAccessDeniedException() {
        Student otherStudent = buildStudent(2L, "other@test.com");
        Submission submission = buildSubmission(1L, buildAssignment(1L), otherStudent);
        when(currentUser.getEmail()).thenReturn(STUDENT_EMAIL);
        when(currentUser.hasRole("STUDENT")).thenReturn(true);
        when(submissionRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(submission));

        assertThatThrownBy(() -> submissionService.findById(1L))
                .isInstanceOf(AccessDeniedException.class);
    }

    // --- getStatus ---

    @Test
    void getStatus_happyPath_returnsPendingStatus() {
        Student student = buildStudent(1L, STUDENT_EMAIL);
        Submission submission = buildSubmission(1L, buildAssignment(1L), student);
        when(currentUser.getEmail()).thenReturn(STUDENT_EMAIL);
        when(currentUser.hasRole("STUDENT")).thenReturn(true);
        when(submissionRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(submission));

        SubmissionStatusResponse response = submissionService.getStatus(1L);

        assertThat(response.status()).isEqualTo(SubmissionStatus.PENDING);
        assertThat(response.score()).isNull();
    }

    @Test
    void getStatus_notFound_throwsResourceNotFoundException() {
        when(submissionRepository.findByIdWithDetails(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> submissionService.getStatus(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // --- listByAssignment ---

    @Test
    void listByAssignment_happyPath_returnsAllSubmissions() {
        when(assignmentRepository.existsById(1L)).thenReturn(true);
        Student student = buildStudent(1L, STUDENT_EMAIL);
        Assignment assignment = buildAssignment(1L);
        when(submissionRepository.findAllByAssignmentIdOrderBySubmittedAtDesc(1L))
                .thenReturn(List.of(buildSubmission(1L, assignment, student)));

        List<SubmissionResponse> result = submissionService.listByAssignment(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).assignmentId()).isEqualTo(1L);
    }

    @Test
    void listByAssignment_assignmentNotFound_throwsResourceNotFoundException() {
        when(assignmentRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> submissionService.listByAssignment(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // --- getMyLatestSubmission ---

    @Test
    void getMyLatestSubmission_hasSubmission_returnsLatest() {
        Student student = buildStudent(1L, STUDENT_EMAIL);
        Assignment assignment = buildAssignment(1L);
        when(currentUser.getEmail()).thenReturn(STUDENT_EMAIL);
        when(assignmentRepository.existsById(1L)).thenReturn(true);
        when(studentRepository.findByUser_Email(STUDENT_EMAIL)).thenReturn(Optional.of(student));
        when(submissionRepository.findAllByAssignmentIdAndStudentIdOrderBySubmittedAtDesc(1L, 1L))
                .thenReturn(List.of(buildSubmission(1L, assignment, student)));

        Optional<SubmissionResponse> result = submissionService.getMyLatestSubmission(1L);

        assertThat(result).isPresent();
        assertThat(result.get().studentEmail()).isEqualTo(STUDENT_EMAIL);
    }

    @Test
    void getMyLatestSubmission_noSubmissions_returnsEmpty() {
        Student student = buildStudent(1L, STUDENT_EMAIL);
        when(currentUser.getEmail()).thenReturn(STUDENT_EMAIL);
        when(assignmentRepository.existsById(1L)).thenReturn(true);
        when(studentRepository.findByUser_Email(STUDENT_EMAIL)).thenReturn(Optional.of(student));
        when(submissionRepository.findAllByAssignmentIdAndStudentIdOrderBySubmittedAtDesc(1L, 1L))
                .thenReturn(List.of());

        Optional<SubmissionResponse> result = submissionService.getMyLatestSubmission(1L);

        assertThat(result).isEmpty();
    }

    @Test
    void getMyLatestSubmission_assignmentNotFound_throwsResourceNotFoundException() {
        when(assignmentRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> submissionService.getMyLatestSubmission(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // --- helpers ---

    private Assignment buildAssignment(Long id) {
        Teacher teacher = buildTeacher(1L, 10L);
        Course course = Course.builder()
                .name("Test Course")
                .academicYear(2025)
                .semester(1)
                .createdBy(teacher)
                .build();
        ReflectionTestUtils.setField(course, "id", 1L);
        ReflectionTestUtils.setField(course, "isActive", true);

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

    private Student buildStudent(Long studentId, String email) {
        User user = User.builder()
                .email(email)
                .firstName("Test")
                .lastName("Student")
                .role(Role.STUDENT)
                .build();
        user.setId(studentId + 100L);
        Student student = Student.builder().user(user).build();
        student.setId(studentId);
        return student;
    }

    private Teacher buildTeacher(Long teacherId, Long userId) {
        User user = User.builder()
                .email("teacher@test.com")
                .firstName("Test")
                .lastName("Teacher")
                .role(Role.TEACHER)
                .build();
        user.setId(userId);
        Teacher teacher = Teacher.builder().user(user).build();
        teacher.setId(teacherId);
        return teacher;
    }

    private Submission buildSubmission(Long id, Assignment assignment, Student student) {
        Submission submission = Submission.builder()
                .assignment(assignment)
                .student(student)
                .codeContent("public class Main {}")
                .build();
        ReflectionTestUtils.setField(submission, "id", id);
        return submission;
    }
}
