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
import ua.kpi.grader.course.entity.FeedbackLevel;
import ua.kpi.grader.course.entity.Language;
import ua.kpi.grader.course.entity.ProgrammingTask;
import ua.kpi.grader.course.repository.AssignmentRepository;
import ua.kpi.grader.gitlab.client.GitLabApiClient;
import ua.kpi.grader.gitlab.client.dto.GitLabJobDto;
import ua.kpi.grader.gitlab.service.GitLabSubmissionService;
import ua.kpi.grader.security.CurrentUser;
import ua.kpi.grader.submission.dto.AttemptResponse;
import ua.kpi.grader.submission.dto.AttemptStatusResponse;
import ua.kpi.grader.submission.dto.TestReportResponse;
import ua.kpi.grader.submission.dto.CreateSubmissionRequest;
import ua.kpi.grader.submission.dto.SubmissionResponse;
import ua.kpi.grader.submission.dto.SubmissionStatusResponse;
import ua.kpi.grader.submission.entity.Attempt;
import ua.kpi.grader.submission.entity.Submission;
import ua.kpi.grader.submission.entity.SubmissionStatus;
import ua.kpi.grader.submission.feedback.TestCaseStatus;
import ua.kpi.grader.submission.feedback.TestRunStatus;
import ua.kpi.grader.submission.repository.AttemptRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubmissionServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private AttemptRepository attemptRepository;

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private CurrentUser currentUser;

    @Mock
    private GitLabSubmissionService gitLabSubmissionService;

    @Mock
    private GitLabApiClient gitLabApiClient;

    @Mock
    private SubmissionAccess submissionAccess;

    @InjectMocks
    private SubmissionServiceImpl submissionService;

    private static final String STUDENT_EMAIL = "student@test.com";

    // --- createSubmission (first attempt, new submission) ---

    @Test
    void createSubmission_firstAttempt_createsSubmissionAndAttempt() {
        Assignment assignment = buildAssignment(1L);
        Student student = buildStudent(1L, STUDENT_EMAIL);
        when(currentUser.getEmail()).thenReturn(STUDENT_EMAIL);
        when(assignmentRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(assignment));
        when(studentRepository.findByUser_Email(STUDENT_EMAIL)).thenReturn(Optional.of(student));

        Submission savedSubmission = buildSubmission(1L, assignment, student);
        when(submissionAccess.getOrCreate(assignment, student)).thenReturn(savedSubmission);
        when(attemptRepository.findMaxAttemptNumber(1L)).thenReturn(0);

        Attempt savedAttempt = buildAttempt(1L, savedSubmission, 1, "public class Main {}");
        when(attemptRepository.save(any(Attempt.class))).thenReturn(savedAttempt);

        AttemptResponse response = submissionService.createSubmission(1L,
                new CreateSubmissionRequest("public class Main {}"));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.attemptNumber()).isEqualTo(1);
        assertThat(response.status()).isEqualTo(SubmissionStatus.PENDING);
        verify(submissionAccess).getOrCreate(assignment, student);
        verify(attemptRepository).save(any(Attempt.class));
        verify(gitLabSubmissionService).triggerPipeline(eq(savedSubmission), eq(savedAttempt));
    }

    @Test
    void createSubmission_secondAttempt_reusesExistingSubmission() {
        Assignment assignment = buildAssignment(1L);
        Student student = buildStudent(1L, STUDENT_EMAIL);
        Submission existingSubmission = buildSubmission(1L, assignment, student);
        when(currentUser.getEmail()).thenReturn(STUDENT_EMAIL);
        when(assignmentRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(assignment));
        when(studentRepository.findByUser_Email(STUDENT_EMAIL)).thenReturn(Optional.of(student));
        when(submissionAccess.getOrCreate(assignment, student)).thenReturn(existingSubmission);
        when(attemptRepository.findMaxAttemptNumber(1L)).thenReturn(1);

        Attempt savedAttempt = buildAttempt(2L, existingSubmission, 2, "updated code");
        when(attemptRepository.save(any(Attempt.class))).thenReturn(savedAttempt);

        AttemptResponse response = submissionService.createSubmission(1L,
                new CreateSubmissionRequest("updated code"));

        assertThat(response.attemptNumber()).isEqualTo(2);
        verify(submissionAccess).getOrCreate(assignment, student);
        verify(gitLabSubmissionService).triggerPipeline(eq(existingSubmission), eq(savedAttempt));
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
        when(submissionRepository.findAllByAssignmentIdOrderByUpdatedAtDesc(1L))
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

    // --- getMySubmission ---

    @Test
    void getMySubmission_hasSubmission_returnsIt() {
        Student student = buildStudent(1L, STUDENT_EMAIL);
        Assignment assignment = buildAssignment(1L);
        when(currentUser.getEmail()).thenReturn(STUDENT_EMAIL);
        when(assignmentRepository.existsById(1L)).thenReturn(true);
        when(studentRepository.findByUser_Email(STUDENT_EMAIL)).thenReturn(Optional.of(student));
        when(submissionRepository.findByAssignmentIdAndStudentId(1L, 1L))
                .thenReturn(Optional.of(buildSubmission(1L, assignment, student)));

        Optional<SubmissionResponse> result = submissionService.getMySubmission(1L);

        assertThat(result).isPresent();
        assertThat(result.get().studentEmail()).isEqualTo(STUDENT_EMAIL);
    }

    @Test
    void getMySubmission_noSubmission_returnsEmpty() {
        Student student = buildStudent(1L, STUDENT_EMAIL);
        when(currentUser.getEmail()).thenReturn(STUDENT_EMAIL);
        when(assignmentRepository.existsById(1L)).thenReturn(true);
        when(studentRepository.findByUser_Email(STUDENT_EMAIL)).thenReturn(Optional.of(student));
        when(submissionRepository.findByAssignmentIdAndStudentId(1L, 1L))
                .thenReturn(Optional.empty());

        Optional<SubmissionResponse> result = submissionService.getMySubmission(1L);

        assertThat(result).isEmpty();
    }

    @Test
    void getMySubmission_assignmentNotFound_throwsResourceNotFoundException() {
        when(assignmentRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> submissionService.getMySubmission(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // --- applyGitLabResult / structured feedback ---

    private static final String P = "##GRADER## ";
    private static final String FAILING_LOG = P + "{\"event\":\"plan\",\"tests\":[\"t_ok\",\"t_bad\"]}\n"
            + P + "{\"event\":\"start\",\"name\":\"t_ok\"}\n"
            + P + "{\"event\":\"result\",\"name\":\"t_ok\",\"status\":\"PASSED\",\"durationMs\":1}\n"
            + P + "{\"event\":\"start\",\"name\":\"t_bad\"}\n"
            + P + "{\"event\":\"result\",\"name\":\"t_bad\",\"status\":\"FAILED\","
            + "\"expected\":\"5\",\"actual\":\"6\",\"message\":\"EXPECT_EQ\"}\n"
            + P + "{\"event\":\"end\"}\n";

    private Attempt attemptWithPipeline(FeedbackLevel level) {
        Assignment assignment = buildAssignment(1L);
        ProgrammingTask task = ProgrammingTask.builder()
                .language(Language.CPP)
                .feedbackLevel(level)
                .build();
        ReflectionTestUtils.setField(assignment, "programmingTask", task);
        Submission submission = buildSubmission(1L, assignment, buildStudent(1L, STUDENT_EMAIL));
        Attempt attempt = buildAttempt(7L, submission, 1, "code");
        attempt.startPipeline(55L);
        when(attemptRepository.findByGitlabPipelineId(55L)).thenReturn(Optional.of(attempt));
        return attempt;
    }

    private void stubLog(String log) {
        when(gitLabApiClient.getPipelineJobs(9, 55)).thenReturn(List.of(new GitLabJobDto(3, "test", "failed", null)));
        when(gitLabApiClient.getJobLog(9, 3)).thenReturn(log);
    }

    private Attempt gradedAttempt(FeedbackLevel level) {
        Attempt attempt = attemptWithPipeline(level);
        stubLog(FAILING_LOG);
        submissionService.applyGitLabResult(9L, 55L, "failed");
        return attempt;
    }

    private void asStudent() {
        when(currentUser.hasRole("STUDENT")).thenReturn(true);
        when(currentUser.getEmail()).thenReturn(STUDENT_EMAIL);
        when(currentUser.isStaff()).thenReturn(false);
    }

    @Test
    void applyGitLabResult_failingTests_persistsStructuredResults() {
        Attempt attempt = gradedAttempt(FeedbackLevel.FULL);

        assertThat(attempt.getStatus()).isEqualTo(SubmissionStatus.FAILED);
        assertThat(attempt.getScore()).isZero();
        assertThat(attempt.getTestsPassed()).isEqualTo(1);
        assertThat(attempt.getTestsTotal()).isEqualTo(2);
        assertThat(attempt.getTestRunStatus()).isEqualTo(TestRunStatus.COMPLETED);
        assertThat(attempt.getTestResults()).hasSize(2);
        assertThat(attempt.getTestResults().get(1).getPosition()).isEqualTo(1);
        assertThat(attempt.getTestResults().get(1).getExpected()).isEqualTo("5");
        assertThat(attempt.getTestResults().get(1).getActual()).isEqualTo("6");
        assertThat(attempt.getTestResults().get(1).getAttempt()).isSameAs(attempt);
    }

    @Test
    void applyGitLabResult_secondCall_replacesPreviousResults() {
        Attempt attempt = gradedAttempt(FeedbackLevel.FULL);

        submissionService.applyGitLabResult(9L, 55L, "failed");

        assertThat(attempt.getTestResults()).hasSize(2);
    }

    @Test
    void applyGitLabResult_successButReportShowsFailure_downgradesToFailed() {
        Attempt attempt = attemptWithPipeline(FeedbackLevel.FULL);
        stubLog(FAILING_LOG);

        submissionService.applyGitLabResult(9L, 55L, "success");

        assertThat(attempt.getStatus()).isEqualTo(SubmissionStatus.FAILED);
        assertThat(attempt.getScore()).isZero();
    }

    @Test
    void applyGitLabResult_logWithoutDetails_keepsPipelineStatusAndRawLogOnly() {
        Attempt attempt = attemptWithPipeline(FeedbackLevel.FULL);
        stubLog("all good\n");

        submissionService.applyGitLabResult(9L, 55L, "success");

        assertThat(attempt.getStatus()).isEqualTo(SubmissionStatus.PASSED);
        assertThat(attempt.getScore()).isEqualTo(100);
        assertThat(attempt.getTestRunStatus()).isNull();
        assertThat(attempt.getTestsTotal()).isNull();
        assertThat(attempt.getPipelineOutput()).contains("all good");
    }

    @Test
    void applyGitLabResult_compileError_storesCompilerOutput() {
        Attempt attempt = attemptWithPipeline(FeedbackLevel.FULL);
        stubLog(P + "{\"event\":\"compile_failed\"}\nsolution.cpp:1: error: boom\nERROR: Job failed: exit code 1\n");

        submissionService.applyGitLabResult(9L, 55L, "failed");

        assertThat(attempt.getTestRunStatus()).isEqualTo(TestRunStatus.COMPILE_ERROR);
        assertThat(attempt.getCompileOutput()).isEqualTo("solution.cpp:1: error: boom");
    }

    @Test
    void getAttemptStatus_student_fullLevel_seesValuesButNoRawLog() {
        Attempt attempt = gradedAttempt(FeedbackLevel.FULL);
        when(attemptRepository.findByIdWithDetails(7L)).thenReturn(Optional.of(attempt));
        asStudent();

        AttemptStatusResponse response = submissionService.getAttemptStatus(7L);

        TestReportResponse report = response.testReport();
        assertThat(report.detailsAvailable()).isTrue();
        assertThat(report.feedbackLevel()).isEqualTo(FeedbackLevel.FULL);
        assertThat(report.tests()).hasSize(2);
        assertThat(report.tests().get(1).expected()).isEqualTo("5");
        assertThat(response.pipelineOutput()).isNull();
    }

    @Test
    void getAttemptStatus_student_fullLevel_withoutReport_fallsBackToRawLog() {
        Attempt attempt = attemptWithPipeline(FeedbackLevel.FULL);
        stubLog("test_runner: test.cpp:5: Assertion `add(1, 2) == 3' failed.\n");
        submissionService.applyGitLabResult(9L, 55L, "failed");
        when(attemptRepository.findByIdWithDetails(7L)).thenReturn(Optional.of(attempt));
        asStudent();

        AttemptStatusResponse response = submissionService.getAttemptStatus(7L);

        assertThat(response.testReport().detailsAvailable()).isFalse();
        assertThat(response.pipelineOutput()).contains("Assertion");
    }

    @Test
    void getAttemptStatus_student_namesOnly_withoutReport_hidesRawLog() {
        Attempt attempt = attemptWithPipeline(FeedbackLevel.NAMES_ONLY);
        stubLog("test_runner: test.cpp:5: Assertion `add(1, 2) == 3' failed.\n");
        submissionService.applyGitLabResult(9L, 55L, "failed");
        when(attemptRepository.findByIdWithDetails(7L)).thenReturn(Optional.of(attempt));
        asStudent();

        assertThat(submissionService.getAttemptStatus(7L).pipelineOutput()).isNull();
    }

    @Test
    void getAttemptStatus_student_namesOnly_hidesValuesAndLog() {
        Attempt attempt = gradedAttempt(FeedbackLevel.NAMES_ONLY);
        when(attemptRepository.findByIdWithDetails(7L)).thenReturn(Optional.of(attempt));
        asStudent();

        AttemptStatusResponse response = submissionService.getAttemptStatus(7L);

        TestReportResponse report = response.testReport();
        assertThat(report.tests()).extracting("name").containsExactly("t_ok", "t_bad");
        assertThat(report.tests()).extracting("status")
                .containsExactly(TestCaseStatus.PASSED, TestCaseStatus.FAILED);
        assertThat(report.tests().get(1).expected()).isNull();
        assertThat(report.tests().get(1).actual()).isNull();
        assertThat(report.tests().get(1).message()).isNull();
        assertThat(response.pipelineOutput()).isNull();
    }

    @Test
    void getAttemptStatus_student_summary_showsOnlyCounts() {
        Attempt attempt = gradedAttempt(FeedbackLevel.SUMMARY);
        when(attemptRepository.findByIdWithDetails(7L)).thenReturn(Optional.of(attempt));
        asStudent();

        AttemptStatusResponse response = submissionService.getAttemptStatus(7L);

        assertThat(response.testReport().passed()).isEqualTo(1);
        assertThat(response.testReport().total()).isEqualTo(2);
        assertThat(response.testReport().tests()).isEmpty();
        assertThat(response.pipelineOutput()).isNull();
    }

    @Test
    void listAttempts_teacher_summaryLevel_stillSeesEverything() {
        Attempt attempt = gradedAttempt(FeedbackLevel.SUMMARY);
        when(submissionRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(attempt.getSubmission()));
        when(attemptRepository.findAllBySubmissionIdOrderByAttemptNumberDesc(1L)).thenReturn(List.of(attempt));
        when(currentUser.hasRole("STUDENT")).thenReturn(false);
        when(currentUser.isStaff()).thenReturn(true);

        List<AttemptResponse> responses = submissionService.listAttempts(1L);

        TestReportResponse report = responses.getFirst().testReport();
        assertThat(report.feedbackLevel()).isEqualTo(FeedbackLevel.FULL);
        assertThat(report.tests().get(1).expected()).isEqualTo("5");
        assertThat(responses.getFirst().pipelineOutput()).isNotNull();
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
                .build();
        ReflectionTestUtils.setField(submission, "id", id);
        return submission;
    }

    private Attempt buildAttempt(Long id, Submission submission, int attemptNumber, String code) {
        Attempt attempt = Attempt.builder()
                .submission(submission)
                .attemptNumber(attemptNumber)
                .codeContent(code)
                .build();
        ReflectionTestUtils.setField(attempt, "id", id);
        return attempt;
    }
}
