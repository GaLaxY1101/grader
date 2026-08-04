package ua.kpi.grader.template.service;

import ua.kpi.grader.template.dto.CreateTemplateAssignmentRequest;
import ua.kpi.grader.template.dto.TemplateAssignmentResponse;
import ua.kpi.grader.template.dto.UpdateTemplateAssignmentRequest;

import java.util.List;

public interface TemplateAssignmentService {

    /**
     * Returns all assignments of a template; requires view access on the template.
     */
    List<TemplateAssignmentResponse> findAllByTemplate(Long templateId);

    /**
     * Returns a single template assignment; requires view access on the parent template.
     */
    TemplateAssignmentResponse findById(Long id);

    /**
     * Creates a new template assignment (optionally with a programming task) under
     * the given template. Requires edit access on the parent template.
     */
    TemplateAssignmentResponse createAssignment(Long templateId, CreateTemplateAssignmentRequest request);

    /**
     * Updates a template assignment. If programmingTask is present it is
     * created/updated; if null any existing programming task is removed.
     * Requires edit access on the parent template.
     */
    TemplateAssignmentResponse updateAssignment(Long id, UpdateTemplateAssignmentRequest request);

    /**
     * Deletes a template assignment. Requires edit access on the parent template.
     */
    void deleteAssignment(Long id);
}
