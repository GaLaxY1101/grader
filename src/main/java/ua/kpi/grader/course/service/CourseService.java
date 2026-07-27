package ua.kpi.grader.course.service;

import ua.kpi.grader.course.dto.*;

import java.util.List;

public interface CourseService {

    /**
     * Returns all active courses.
     */
    List<CourseResponse> findAllActive();

    /**
     * Returns a course with its teachers, enrolled students, and assignments.
     */
    CourseDetailResponse findById(Long id);

    /**
     * Creates a new course owned by the currently authenticated teacher.
     */
    CourseResponse createCourse(CreateCourseRequest request);

    /**
     * Updates mutable fields of an existing course.
     */
    CourseResponse updateCourse(Long id, UpdateCourseRequest request);

    /**
     * Soft-deletes a course by setting is_active = false.
     */
    void deactivateCourse(Long id);

    /**
     * Enrolls a student in a course.
     */
    EnrolledStudentResponse enrollStudent(Long courseId, Long studentId);

    /**
     * Enrolls all active members of an academic group into a course.
     * Students already enrolled are skipped silently. Returns only the
     * newly-enrolled students.
     */
    List<EnrolledStudentResponse> enrollGroup(Long courseId, Long groupId);

    /**
     * Unenrolls (soft-drops) every ACTIVE enrollment for members of the given
     * group. Returns the IDs of students whose status was flipped to DROPPED.
     */
    List<Long> unenrollGroup(Long courseId, Long groupId);

    /**
     * Drops a student from a course (sets status = DROPPED).
     */
    void unenrollStudent(Long courseId, Long studentId);

    /**
     * Returns all enrolled students of a course.
     */
    List<EnrolledStudentResponse> findStudents(Long courseId);

    /**
     * Adds a teacher to a course.
     */
    CourseTeacherResponse addTeacher(Long courseId, Long teacherId);

    /**
     * Removes a teacher from a course.
     */
    void removeTeacher(Long courseId, Long teacherId);

    /**
     * Returns all teachers of a course.
     */
    List<CourseTeacherResponse> findTeachers(Long courseId);
}
