package ua.kpi.grader.course.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import ua.kpi.grader.user.entity.Student;
import ua.kpi.grader.user.entity.Teacher;
import ua.kpi.grader.user.repository.StudentRepository;
import ua.kpi.grader.security.CurrentUser;
import ua.kpi.grader.user.repository.TeacherRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CourseServiceImpl implements CourseService {

    private final CourseRepository courseRepository;
    private final CourseEnrollmentRepository enrollmentRepository;
    private final CourseTeacherRepository courseTeacherRepository;
    private final AssignmentRepository assignmentRepository;
    private final TeacherRepository teacherRepository;
    private final StudentRepository studentRepository;
    private final AcademicGroupRepository groupRepository;
    private final GroupStudentRepository groupStudentRepository;
    private final CurrentUser currentUser;

    /**
     * Returns a page of active courses. A non-blank {@code query} matches the
     * course name or the code of any academic group whose members are currently
     * ACTIVE-enrolled in the course. A non-null {@code groupId} restricts the
     * result to courses with at least one active enrollment from a member of
     * that group.
     *
     * @param query    free-text search term (blank/null disables text filter)
     * @param groupId  academic group id filter (null disables)
     * @param pageable paging & sort
     * @return page of CourseResponse DTOs wrapped in PageResponse
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<CourseResponse> findAll(String query, Long groupId, Pageable pageable) {
        String normalized = normalizeQuery(query);
        Page<CourseResponse> page = courseRepository
                .search(normalized, groupId, true, pageable)
                .map(CourseResponse::from);
        return PageResponse.from(page);
    }

    private static String normalizeQuery(String query) {
        if (query == null) return null;
        String trimmed = query.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase();
    }

    /**
     * Returns a course with its teachers, enrolled students, and active assignments.
     *
     * @param id the course ID
     * @return CourseDetailResponse
     * @throws ResourceNotFoundException if the course does not exist
     */
    @Override
    @Transactional(readOnly = true)
    public CourseDetailResponse findById(Long id) {
        Course course = findCourseOrThrow(id);

        List<CourseTeacherResponse> teachers = courseTeacherRepository
                .findAllByCourseIdWithTeacherUser(id).stream()
                .map(CourseTeacherResponse::from)
                .toList();

        List<EnrolledStudentResponse> students = mapEnrollmentsWithGroup(
                enrollmentRepository.findAllByCourseIdWithStudentUser(id));

        List<AssignmentResponse> assignments = assignmentRepository
                .findAllByCourseIdAndIsActiveTrue(id).stream()
                .map(AssignmentResponse::from)
                .toList();

        return CourseDetailResponse.from(course, teachers, students, assignments);
    }

    /**
     * Creates a new course owned by the currently authenticated teacher.
     * The teacher is resolved from the Keycloak JWT email claim.
     *
     * @param request the creation payload
     * @return the persisted CourseResponse DTO
     * @throws ResourceNotFoundException if no teacher profile exists for the current user
     */
    @Override
    @Transactional
    public CourseResponse createCourse(CreateCourseRequest request) {
        // TODO: resolve Keycloak UUID to internal teacher id
        // This will be done when we implement user sync in auth module
        String email = currentUser.getEmail();
        Teacher teacher = teacherRepository.findByUser_Email(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Teacher not found for user: " + email));

        Course course = Course.builder()
                .name(request.name())
                .description(request.description())
                .academicYear(request.academicYear())
                .semester(request.semester())
                .createdBy(teacher)
                .build();

        return CourseResponse.from(courseRepository.save(course));
    }

    /**
     * Updates mutable fields of an existing course.
     *
     * @param id      the course ID
     * @param request the update payload
     * @return the updated CourseResponse DTO
     * @throws ResourceNotFoundException if the course does not exist
     */
    @Override
    @Transactional
    public CourseResponse updateCourse(Long id, UpdateCourseRequest request) {
        Course course = findCourseOrThrow(id);
        course.update(request.name(), request.description(), request.academicYear(), request.semester());
        return CourseResponse.from(course);
    }

    /**
     * Soft-deletes a course by setting is_active = false.
     *
     * @param id the course ID
     * @throws ResourceNotFoundException if the course does not exist
     */
    @Override
    @Transactional
    public void deactivateCourse(Long id) {
        Course course = findCourseOrThrow(id);
        course.deactivate();
    }

    /**
     * Enrolls a student in a course.
     *
     * @param courseId  the course ID
     * @param studentId the student ID
     * @return the created EnrolledStudentResponse DTO
     * @throws ResourceNotFoundException if the course or student does not exist
     * @throws IllegalStateException     if the student is already enrolled
     */
    @Override
    @Transactional
    public EnrolledStudentResponse enrollStudent(Long courseId, Long studentId) {
        Course course = findCourseOrThrow(courseId);
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Student not found with id: " + studentId));

        if (enrollmentRepository.existsByCourseIdAndStudentId(courseId, studentId)) {
            throw new IllegalStateException(
                    "Student " + studentId + " is already enrolled in course " + courseId);
        }

        CourseEnrollment enrollment = CourseEnrollment.builder()
                .course(course)
                .student(student)
                .build();

        AcademicGroup activeGroup = groupStudentRepository.findActiveByStudentId(studentId)
                .map(GroupStudent::getGroup)
                .orElse(null);
        return EnrolledStudentResponse.from(enrollmentRepository.save(enrollment), activeGroup);
    }

    /**
     * Enrolls all active members of a group into a course. Students with an
     * ACTIVE enrollment are skipped silently; students with a DROPPED enrollment
     * are reactivated. Both make the call idempotent for currently-active members
     * and repeatable after a manual unenroll.
     *
     * @param courseId the course ID
     * @param groupId  the group ID
     * @return list of students that ended up newly active in the course
     *         (freshly enrolled + reactivated); may be empty
     * @throws ResourceNotFoundException if the course or group does not exist
     */
    @Override
    @Transactional
    public List<EnrolledStudentResponse> enrollGroup(Long courseId, Long groupId) {
        Course course = findCourseOrThrow(courseId);
        AcademicGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Group not found with id: " + groupId));

        List<GroupStudent> memberships = groupStudentRepository.findAllByGroupIdWithStudentUser(groupId);
        if (memberships.isEmpty()) {
            return List.of();
        }

        List<Long> studentIds = memberships.stream()
                .map(gs -> gs.getStudent().getId())
                .toList();
        Map<Long, CourseEnrollment> existingByStudentId = enrollmentRepository
                .findAllByCourseIdAndStudentIdIn(courseId, studentIds).stream()
                .collect(Collectors.toMap(ce -> ce.getStudent().getId(), Function.identity()));

        List<EnrolledStudentResponse> affected = new ArrayList<>();
        for (GroupStudent membership : memberships) {
            Student student = membership.getStudent();
            CourseEnrollment existing = existingByStudentId.get(student.getId());
            if (existing != null && "ACTIVE".equals(existing.getStatus())) {
                continue;
            }
            CourseEnrollment enrollment;
            if (existing != null) {
                existing.reactivate();
                enrollment = existing;
            } else {
                enrollment = enrollmentRepository.save(CourseEnrollment.builder()
                        .course(course)
                        .student(student)
                        .build());
            }
            affected.add(EnrolledStudentResponse.from(enrollment, group));
        }
        return affected;
    }

    /**
     * Drops a student from a course by setting status = DROPPED.
     *
     * @param courseId  the course ID
     * @param studentId the student ID
     * @throws ResourceNotFoundException if the course does not exist or the student is not enrolled
     */
    @Override
    @Transactional
    public void unenrollStudent(Long courseId, Long studentId) {
        findCourseOrThrow(courseId);
        CourseEnrollment enrollment = enrollmentRepository
                .findByCourseIdAndStudentId(courseId, studentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Student " + studentId + " is not enrolled in course " + courseId));
        enrollment.drop();
    }

    /**
     * Soft-drops every ACTIVE enrollment for members of the given group.
     * Non-active or non-existent enrollments are ignored, making the call idempotent.
     *
     * @param courseId the course ID
     * @param groupId  the group ID
     * @return list of student IDs whose enrollment status was changed to DROPPED (may be empty)
     * @throws ResourceNotFoundException if the course or group does not exist
     */
    @Override
    @Transactional
    public List<Long> unenrollGroup(Long courseId, Long groupId) {
        findCourseOrThrow(courseId);
        if (!groupRepository.existsById(groupId)) {
            throw new ResourceNotFoundException("Group not found with id: " + groupId);
        }

        List<GroupStudent> memberships = groupStudentRepository.findAllByGroupIdWithStudentUser(groupId);
        if (memberships.isEmpty()) {
            return List.of();
        }
        List<Long> studentIds = memberships.stream()
                .map(gs -> gs.getStudent().getId())
                .toList();

        List<Long> unenrolled = new ArrayList<>();
        for (CourseEnrollment enrollment : enrollmentRepository
                .findAllByCourseIdAndStudentIdIn(courseId, studentIds)) {
            if (!"ACTIVE".equals(enrollment.getStatus())) {
                continue;
            }
            enrollment.drop();
            unenrolled.add(enrollment.getStudent().getId());
        }
        return unenrolled;
    }

    /**
     * Returns all active enrolled students of a course, with their current active group if any.
     *
     * @param courseId the course ID
     * @return list of EnrolledStudentResponse DTOs
     * @throws ResourceNotFoundException if the course does not exist
     */
    @Override
    @Transactional(readOnly = true)
    public List<EnrolledStudentResponse> findStudents(Long courseId) {
        findCourseOrThrow(courseId);
        return mapEnrollmentsWithGroup(
                enrollmentRepository.findAllByCourseIdWithStudentUser(courseId));
    }

    /**
     * Adds a teacher to a course.
     *
     * @param courseId  the course ID
     * @param teacherId the teacher ID
     * @return the created CourseTeacherResponse DTO
     * @throws ResourceNotFoundException if the course or teacher does not exist
     * @throws IllegalStateException     if the teacher is already added to the course
     */
    @Override
    @Transactional
    public CourseTeacherResponse addTeacher(Long courseId, Long teacherId) {
        Course course = findCourseOrThrow(courseId);
        Teacher teacher = teacherRepository.findById(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Teacher not found with id: " + teacherId));

        if (courseTeacherRepository.existsByCourseIdAndTeacherId(courseId, teacherId)) {
            throw new IllegalStateException(
                    "Teacher " + teacherId + " is already assigned to course " + courseId);
        }

        CourseTeacher courseTeacher = CourseTeacher.builder()
                .course(course)
                .teacher(teacher)
                .build();

        return CourseTeacherResponse.from(courseTeacherRepository.save(courseTeacher));
    }

    /**
     * Removes a teacher from a course.
     *
     * @param courseId  the course ID
     * @param teacherId the teacher ID
     * @throws ResourceNotFoundException if the course does not exist or the teacher is not assigned
     */
    @Override
    @Transactional
    public void removeTeacher(Long courseId, Long teacherId) {
        findCourseOrThrow(courseId);
        CourseTeacher courseTeacher = courseTeacherRepository
                .findByCourseIdAndTeacherId(courseId, teacherId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Teacher " + teacherId + " is not assigned to course " + courseId));
        courseTeacherRepository.delete(courseTeacher);
    }

    /**
     * Returns all teachers of a course.
     *
     * @param courseId the course ID
     * @return list of CourseTeacherResponse DTOs
     * @throws ResourceNotFoundException if the course does not exist
     */
    @Override
    @Transactional(readOnly = true)
    public List<CourseTeacherResponse> findTeachers(Long courseId) {
        findCourseOrThrow(courseId);
        return courseTeacherRepository.findAllByCourseIdWithTeacherUser(courseId).stream()
                .map(CourseTeacherResponse::from)
                .toList();
    }

    private List<EnrolledStudentResponse> mapEnrollmentsWithGroup(List<CourseEnrollment> enrollments) {
        if (enrollments.isEmpty()) {
            return List.of();
        }
        Map<Long, AcademicGroup> activeGroupByStudentId = groupStudentRepository
                .findAllActiveWithGroup().stream()
                .collect(Collectors.toMap(
                        gs -> gs.getStudent().getId(),
                        GroupStudent::getGroup,
                        (a, b) -> a));
        return enrollments.stream()
                .map(ce -> EnrolledStudentResponse.from(
                        ce, activeGroupByStudentId.get(ce.getStudent().getId())))
                .toList();
    }

    private Course findCourseOrThrow(Long id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Course not found with id: " + id));
    }
}
