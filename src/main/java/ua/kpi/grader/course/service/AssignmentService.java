package ua.kpi.grader.course.service;

import ua.kpi.grader.course.dto.AssignmentResponse;
import ua.kpi.grader.course.dto.CreateAssignmentRequest;
import ua.kpi.grader.course.dto.UpdateAssignmentRequest;

import java.util.List;

public interface AssignmentService {

    /**
     * Returns all active assignments for a course.
     */
    List<AssignmentResponse> findAllByCourse(Long courseId);

    /**
     * Returns an active assignment by its ID.
     */
    AssignmentResponse findById(Long id);

    /**
     * Creates a new assignment (and optional task details) for a course,
     * owned by the given teacher (identified by their user ID).
     */
    AssignmentResponse createAssignment(Long courseId, CreateAssignmentRequest request, Long teacherUserId);

    /**
     * Updates mutable fields of an existing assignment.
     */
    AssignmentResponse updateAssignment(Long id, UpdateAssignmentRequest request);

    /**
     * Soft-deletes an assignment by setting is_active = false.
     */
    void deactivateAssignment(Long id);
}
