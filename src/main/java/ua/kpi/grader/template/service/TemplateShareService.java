package ua.kpi.grader.template.service;

import ua.kpi.grader.template.dto.CreateTemplateShareRequest;
import ua.kpi.grader.template.dto.TemplateShareResponse;

import java.util.List;

public interface TemplateShareService {

    /**
     * Lists share grants for a template; requires edit access (owner-only view).
     */
    List<TemplateShareResponse> findAllByTemplate(Long templateId);

    /**
     * Grants view access on a template to another teacher; requires edit access.
     *
     * @throws IllegalStateException if a share for that teacher already exists
     *                               or if the target teacher is the owner
     */
    TemplateShareResponse shareTemplate(Long templateId, CreateTemplateShareRequest request);

    /**
     * Revokes a previously granted share; requires edit access.
     */
    void unshareTemplate(Long templateId, Long teacherId);
}
