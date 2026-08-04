package ua.kpi.grader.course.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import ua.kpi.grader.common.dto.PageResponse;
import ua.kpi.grader.common.exception.ResourceNotFoundException;
import ua.kpi.grader.course.dto.*;
import ua.kpi.grader.course.entity.Course;
import ua.kpi.grader.course.entity.CourseEnrollment;
import ua.kpi.grader.course.entity.CourseTeacher;
import ua.kpi.grader.course.repository.AssignmentRepository;
import ua.kpi.grader.course.repository.CourseEnrollmentRepository;
import ua.kpi.grader.course.repository.CourseRepository;
import ua.kpi.grader.course.repository.CourseTeacherRepository;
import ua.kpi.grader.group.entity.AcademicGroup;
import ua.kpi.grader.group.entity.GroupStudent;
import ua.kpi.grader.group.repository.AcademicGroupRepository;
import ua.kpi.grader.group.repository.GroupStudentRepository;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
    private AcademicGroupRepository groupRepository;

    @Mock
    private GroupStudentRepository groupStudentRepository;

    @Mock
    private CurrentUser currentUser;

    @Mock
    private ua.kpi.grader.template.service.TemplateAccessService templateAccess;

    @Mock
    private ua.kpi.grader.template.repository.TemplateAssignmentRepository templateAssignmentRepository;

    @Mock
    private ua.kpi.grader.template.mapper.TemplateToCourseMapper templateToCourseMapper;

    @InjectMocks
    private CourseServiceImpl courseService;

    // --- findAll ---

    @Test
    void findAll_returnsPageOfActiveCourses_withNormalizedQuery() {
        Course course = buildCourse(1L, "Math");
        Pageable pageable = PageRequest.of(0, 12);
        Page<Course> page = new PageImpl<>(List.of(course), pageable, 1);
        when(courseRepository.search(eq("math"), eq(null), eq(true), eq(pageable)))
                .thenReturn(page);

        PageResponse<CourseResponse> result = courseService.findAll("  Math  ", null, true, pageable);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).name()).isEqualTo("Math");
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.page()).isZero();
    }

    @Test
    void findAll_passesNullQuery_whenBlank() {
        Pageable pageable = PageRequest.of(0, 12);
        when(courseRepository.search(eq(null), eq(5L), eq(true), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        PageResponse<CourseResponse> result = courseService.findAll("   ", 5L, true, pageable);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
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
        CreateCourseRequest request = new CreateCourseRequest("CS101", null, 2024, 1, null);
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
        CreateCourseRequest request = new CreateCourseRequest("CS101", null, 2024, 1, null);
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
        UpdateCourseRequest request = new UpdateCourseRequest("New Name", "desc", 2024, 2);
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

        CourseResponse result = courseService.updateCourse(1L, request);

        assertThat(result.name()).isEqualTo("New Name");
    }

    @Test
    void updateCourse_throwsResourceNotFoundException_whenNotFound() {
        UpdateCourseRequest request = new UpdateCourseRequest("Name", null, 2024, 1);
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
        when(groupStudentRepository.findActiveByStudentId(2L)).thenReturn(Optional.empty());
        when(enrollmentRepository.save(any())).thenReturn(saved);

        EnrolledStudentResponse result = courseService.enrollStudent(1L, 2L);

        assertThat(result.email()).isEqualTo("student@test.com");
        assertThat(result.groupId()).isNull();
        verify(enrollmentRepository).save(any(CourseEnrollment.class));
    }

    @Test
    void enrollStudent_populatesGroupInfo_whenStudentHasActiveGroup() {
        Course course = buildCourse(1L, "CS101");
        Student student = buildStudent(2L, "student@test.com");
        AcademicGroup group = buildGroup(7L, "IP-22");
        GroupStudent membership = GroupStudent.builder().group(group).student(student).build();
        CourseEnrollment saved = CourseEnrollment.builder().course(course).student(student).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(studentRepository.findById(2L)).thenReturn(Optional.of(student));
        when(enrollmentRepository.existsByCourseIdAndStudentId(1L, 2L)).thenReturn(false);
        when(groupStudentRepository.findActiveByStudentId(2L)).thenReturn(Optional.of(membership));
        when(enrollmentRepository.save(any())).thenReturn(saved);

        EnrolledStudentResponse result = courseService.enrollStudent(1L, 2L);

        assertThat(result.groupId()).isEqualTo(7L);
        assertThat(result.groupCode()).isEqualTo("IP-22");
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
        AcademicGroup group = buildGroup(9L, "CS-21");
        GroupStudent membership = GroupStudent.builder().group(group).student(student).build();
        CourseEnrollment enrollment = CourseEnrollment.builder().course(course).student(student).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.findAllByCourseIdWithStudentUser(1L)).thenReturn(List.of(enrollment));
        when(groupStudentRepository.findAllActiveWithGroup()).thenReturn(List.of(membership));

        List<EnrolledStudentResponse> result = courseService.findStudents(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).email()).isEqualTo("bob@test.com");
        assertThat(result.get(0).groupCode()).isEqualTo("CS-21");
    }

    @Test
    void findStudents_throwsResourceNotFoundException_whenCourseNotFound() {
        when(courseRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.findStudents(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // --- enrollGroup ---

    @Test
    void enrollGroup_enrollsAllMembers_whenNoneAlreadyEnrolled() {
        Course course = buildCourse(1L, "CS101");
        AcademicGroup group = buildGroup(5L, "IP-22");
        Student s1 = buildStudent(10L, "a@test.com");
        Student s2 = buildStudent(11L, "b@test.com");
        List<GroupStudent> memberships = List.of(
                GroupStudent.builder().group(group).student(s1).build(),
                GroupStudent.builder().group(group).student(s2).build());
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(groupRepository.findById(5L)).thenReturn(Optional.of(group));
        when(groupStudentRepository.findAllByGroupIdWithStudentUser(5L)).thenReturn(memberships);
        when(enrollmentRepository.findAllByCourseIdAndStudentIdIn(eq(1L), anyList()))
                .thenReturn(List.of());
        when(enrollmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<EnrolledStudentResponse> result = courseService.enrollGroup(1L, 5L);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(r -> "IP-22".equals(r.groupCode()));
        verify(enrollmentRepository, org.mockito.Mockito.times(2)).save(any(CourseEnrollment.class));
    }

    @Test
    void enrollGroup_skipsAlreadyEnrolled_whenPartialOverlap() {
        Course course = buildCourse(1L, "CS101");
        AcademicGroup group = buildGroup(5L, "IP-22");
        Student s1 = buildStudent(10L, "a@test.com");
        Student s2 = buildStudent(11L, "b@test.com");
        Student s3 = buildStudent(12L, "c@test.com");
        List<GroupStudent> memberships = List.of(
                GroupStudent.builder().group(group).student(s1).build(),
                GroupStudent.builder().group(group).student(s2).build(),
                GroupStudent.builder().group(group).student(s3).build());
        CourseEnrollment existing = CourseEnrollment.builder().course(course).student(s2).build();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(groupRepository.findById(5L)).thenReturn(Optional.of(group));
        when(groupStudentRepository.findAllByGroupIdWithStudentUser(5L)).thenReturn(memberships);
        when(enrollmentRepository.findAllByCourseIdAndStudentIdIn(eq(1L), anyList()))
                .thenReturn(List.of(existing));
        when(enrollmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<EnrolledStudentResponse> result = courseService.enrollGroup(1L, 5L);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(EnrolledStudentResponse::studentId)
                .containsExactlyInAnyOrder(10L, 12L);
        verify(enrollmentRepository, org.mockito.Mockito.times(2)).save(any(CourseEnrollment.class));
    }

    @Test
    void enrollGroup_reactivatesDroppedEnrollment_whenStudentPreviouslyRemoved() {
        Course course = buildCourse(1L, "CS101");
        AcademicGroup group = buildGroup(5L, "IP-22");
        Student active = buildStudent(10L, "a@test.com");
        Student dropped = buildStudent(11L, "b@test.com");
        List<GroupStudent> memberships = List.of(
                GroupStudent.builder().group(group).student(active).build(),
                GroupStudent.builder().group(group).student(dropped).build());
        CourseEnrollment activeEnrollment = CourseEnrollment.builder()
                .course(course).student(active).build();
        CourseEnrollment droppedEnrollment = CourseEnrollment.builder()
                .course(course).student(dropped).build();
        droppedEnrollment.drop();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(groupRepository.findById(5L)).thenReturn(Optional.of(group));
        when(groupStudentRepository.findAllByGroupIdWithStudentUser(5L)).thenReturn(memberships);
        when(enrollmentRepository.findAllByCourseIdAndStudentIdIn(eq(1L), anyList()))
                .thenReturn(List.of(activeEnrollment, droppedEnrollment));

        List<EnrolledStudentResponse> result = courseService.enrollGroup(1L, 5L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).studentId()).isEqualTo(11L);
        assertThat(droppedEnrollment.getStatus()).isEqualTo("ACTIVE");
        verify(enrollmentRepository, never()).save(any(CourseEnrollment.class));
    }

    @Test
    void enrollGroup_returnsEmptyList_whenGroupHasNoMembers() {
        Course course = buildCourse(1L, "CS101");
        AcademicGroup group = buildGroup(5L, "IP-22");
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(groupRepository.findById(5L)).thenReturn(Optional.of(group));
        when(groupStudentRepository.findAllByGroupIdWithStudentUser(5L)).thenReturn(List.of());

        List<EnrolledStudentResponse> result = courseService.enrollGroup(1L, 5L);

        assertThat(result).isEmpty();
        verify(enrollmentRepository, never()).save(any(CourseEnrollment.class));
    }

    @Test
    void enrollGroup_throwsResourceNotFoundException_whenCourseNotFound() {
        when(courseRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.enrollGroup(99L, 5L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void enrollGroup_throwsResourceNotFoundException_whenGroupNotFound() {
        Course course = buildCourse(1L, "CS101");
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(groupRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.enrollGroup(1L, 99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // --- unenrollGroup ---

    @Test
    void unenrollGroup_dropsActiveEnrollmentsForGroupMembers() {
        Course course = buildCourse(1L, "CS101");
        AcademicGroup group = buildGroup(5L, "IP-22");
        Student s1 = buildStudent(10L, "a@test.com");
        Student s2 = buildStudent(11L, "b@test.com");
        Student s3 = buildStudent(12L, "c@test.com");
        List<GroupStudent> memberships = List.of(
                GroupStudent.builder().group(group).student(s1).build(),
                GroupStudent.builder().group(group).student(s2).build(),
                GroupStudent.builder().group(group).student(s3).build());
        CourseEnrollment activeS1 = CourseEnrollment.builder().course(course).student(s1).build();
        CourseEnrollment activeS2 = CourseEnrollment.builder().course(course).student(s2).build();
        CourseEnrollment droppedS3 = CourseEnrollment.builder().course(course).student(s3).build();
        droppedS3.drop();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(groupRepository.existsById(5L)).thenReturn(true);
        when(groupStudentRepository.findAllByGroupIdWithStudentUser(5L)).thenReturn(memberships);
        when(enrollmentRepository.findAllByCourseIdAndStudentIdIn(eq(1L), anyList()))
                .thenReturn(List.of(activeS1, activeS2, droppedS3));

        List<Long> result = courseService.unenrollGroup(1L, 5L);

        assertThat(result).containsExactlyInAnyOrder(10L, 11L);
        assertThat(activeS1.getStatus()).isEqualTo("DROPPED");
        assertThat(activeS2.getStatus()).isEqualTo("DROPPED");
        assertThat(droppedS3.getStatus()).isEqualTo("DROPPED");
    }

    @Test
    void unenrollGroup_returnsEmptyList_whenNoActiveEnrollments() {
        Course course = buildCourse(1L, "CS101");
        AcademicGroup group = buildGroup(5L, "IP-22");
        Student s1 = buildStudent(10L, "a@test.com");
        List<GroupStudent> memberships = List.of(
                GroupStudent.builder().group(group).student(s1).build());
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(groupRepository.existsById(5L)).thenReturn(true);
        when(groupStudentRepository.findAllByGroupIdWithStudentUser(5L)).thenReturn(memberships);
        when(enrollmentRepository.findAllByCourseIdAndStudentIdIn(eq(1L), anyList()))
                .thenReturn(List.of());

        List<Long> result = courseService.unenrollGroup(1L, 5L);

        assertThat(result).isEmpty();
    }

    @Test
    void unenrollGroup_returnsEmptyList_whenGroupHasNoMembers() {
        Course course = buildCourse(1L, "CS101");
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(groupRepository.existsById(5L)).thenReturn(true);
        when(groupStudentRepository.findAllByGroupIdWithStudentUser(5L)).thenReturn(List.of());

        List<Long> result = courseService.unenrollGroup(1L, 5L);

        assertThat(result).isEmpty();
    }

    @Test
    void unenrollGroup_throwsResourceNotFoundException_whenCourseNotFound() {
        when(courseRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.unenrollGroup(99L, 5L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void unenrollGroup_throwsResourceNotFoundException_whenGroupNotFound() {
        Course course = buildCourse(1L, "CS101");
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(groupRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> courseService.unenrollGroup(1L, 99L))
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

    private AcademicGroup buildGroup(Long id, String code) {
        AcademicGroup group = AcademicGroup.builder()
                .code(code)
                .yearOfCreation(2022)
                .build();
        group.setId(id);
        return group;
    }
}
