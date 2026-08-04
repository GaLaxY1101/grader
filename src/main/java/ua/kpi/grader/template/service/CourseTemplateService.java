package ua.kpi.grader.template.service;

import org.springframework.data.domain.Pageable;
import ua.kpi.grader.common.dto.PageResponse;
import ua.kpi.grader.template.dto.CourseTemplateDetailResponse;
import ua.kpi.grader.template.dto.CourseTemplateResponse;
import ua.kpi.grader.template.dto.CreateCourseTemplateRequest;
import ua.kpi.grader.template.dto.UpdateCourseTemplateRequest;

public interface CourseTemplateService {

    /**
     * Returns a page of templates visible to the current user (owned or shared;
     * ADMIN sees all). Optional case-insensitive name filter.
     */
    PageResponse<CourseTemplateResponse> findVisible(String query, Pageable pageable);

    /**
     * Returns a template with its assignments; requires view access.
     */
    CourseTemplateDetailResponse findById(Long id);

    /**
     * Creates a new empty template owned by the current teacher.
     */
    CourseTemplateResponse createTemplate(CreateCourseTemplateRequest request);

    /**
     * Updates mutable fields on the template; requires edit access.
     */
    CourseTemplateResponse updateTemplate(Long id, UpdateCourseTemplateRequest request);

    /**
     * Deletes a template together with its assignments, programming tasks, test
     * cases, and shares. Requires edit access.
     */
    void deleteTemplate(Long id);

    /**
     * Deep-copies a template into a new one owned by the current teacher.
     * Requires view access on the source. Shares are not carried over.
     */
    CourseTemplateResponse copyTemplate(Long id);
}
