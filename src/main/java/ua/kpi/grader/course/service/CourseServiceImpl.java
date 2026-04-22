package ua.kpi.grader.course.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.kpi.grader.common.exception.ResourceNotFoundException;
import ua.kpi.grader.course.dto.*;
import ua.kpi.grader.course.entity.Course;
import ua.kpi.grader.course.entity.CourseEnrollment;
import ua.kpi.grader.course.entity.CourseTeacher;
import ua.kpi.grader.course.repository.AssignmentRepository;
import ua.kpi.grader.course.repository.CourseEnrollmentRepository;
import ua.kpi.grader.course.repository.CourseRepository;
import ua.kpi.grader.course.repository.CourseTeacherRepository;
import ua.kpi.grader.user.entity.Student;
import ua.kpi.grader.user.entity.Teacher;
import ua.kpi.grader.user.repository.StudentRepository;
import ua.kpi.grader.security.CurrentUser;
import ua.kpi.grader.user.repository.TeacherRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CourseServiceImpl implements CourseService {

    private final CourseRepository courseRepository;
    private final CourseEnrollmentRepository enrollmentRepository;
    private final CourseTeacherRepository courseTeacherRepository;
    private final AssignmentRepository assignmentRepository;
    private final TeacherRepository teacherRepository;
    private final StudentRepository studentRepository;
    private final CurrentUser currentUser;

    /**
     * Returns all active courses (is_active = true).
     *
     * @return list of active CourseResponse DTOs
     */
    @Override
    @Transactional(readOnly = true)
    public List<CourseResponse> findAllActive() {
        return courseRepository.findAllByIsActiveTrue().stream()
                .map(CourseResponse::from)
                .toList();
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

        List<EnrolledStudentResponse> students = enrollmentRepository
                .findAllByCourseIdWithStudentUser(id).stream()
                .map(EnrolledStudentResponse::from)
                .toList();

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
                .startDate(request.startDate())
                .endDate(request.endDate())
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
        course.update(request.name(), request.description(), request.academicYear(),
                request.semester(), request.startDate(), request.endDate());
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

        return EnrolledStudentResponse.from(enrollmentRepository.save(enrollment));
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
     * Returns all enrolled students of a course.
     *
     * @param courseId the course ID
     * @return list of EnrolledStudentResponse DTOs
     * @throws ResourceNotFoundException if the course does not exist
     */
    @Override
    @Transactional(readOnly = true)
    public List<EnrolledStudentResponse> findStudents(Long courseId) {
        findCourseOrThrow(courseId);
        return enrollmentRepository.findAllByCourseIdWithStudentUser(courseId).stream()
                .map(EnrolledStudentResponse::from)
                .toList();
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

    private Course findCourseOrThrow(Long id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Course not found with id: " + id));
    }
}
