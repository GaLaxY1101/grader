package ua.kpi.grader.template.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.kpi.grader.common.exception.ResourceNotFoundException;
import ua.kpi.grader.security.CurrentUser;
import ua.kpi.grader.template.entity.CourseTemplate;
import ua.kpi.grader.template.repository.CourseTemplateRepository;
import ua.kpi.grader.template.repository.TemplateShareRepository;
import ua.kpi.grader.user.entity.Teacher;
import ua.kpi.grader.user.repository.TeacherRepository;

@Service
@RequiredArgsConstructor
public class TemplateAccessService {

    private static final String ROLE_ADMIN = "ADMIN";

    private final CourseTemplateRepository templateRepository;
    private final TemplateShareRepository shareRepository;
    private final TeacherRepository teacherRepository;
    private final CurrentUser currentUser;

    /**
     * Resolves the currently authenticated user to their Teacher profile.
     *
     * @throws ResourceNotFoundException if no teacher profile exists for the current user
     */
    @Transactional(readOnly = true)
    public Teacher currentTeacher() {
        String email = currentUser.getEmail();
        return teacherRepository.findByUser_Email(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Teacher not found for user: " + email));
    }

    /**
     * Loads a template and verifies the current user may view it: owner, shared-with, or ADMIN.
     *
     * @throws ResourceNotFoundException if the template does not exist
     * @throws AccessDeniedException     if the current user lacks view access
     */
    @Transactional(readOnly = true)
    public CourseTemplate requireView(Long templateId) {
        CourseTemplate template = findOrThrow(templateId);
        if (currentUser.hasRole(ROLE_ADMIN)) {
            return template;
        }
        Long teacherId = currentTeacher().getId();
        if (isOwner(template, teacherId) || isSharedWith(templateId, teacherId)) {
            return template;
        }
        throw new AccessDeniedException("No view access to template " + templateId);
    }

    /**
     * Loads a template and verifies the current user may edit it: owner or ADMIN.
     *
     * @throws ResourceNotFoundException if the template does not exist
     * @throws AccessDeniedException     if the current user is not the owner and not ADMIN
     */
    @Transactional(readOnly = true)
    public CourseTemplate requireEdit(Long templateId) {
        CourseTemplate template = findOrThrow(templateId);
        if (currentUser.hasRole(ROLE_ADMIN)) {
            return template;
        }
        Long teacherId = currentTeacher().getId();
        if (isOwner(template, teacherId)) {
            return template;
        }
        throw new AccessDeniedException("Only the template owner can perform this action");
    }

    private CourseTemplate findOrThrow(Long templateId) {
        return templateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Template not found with id: " + templateId));
    }

    private boolean isOwner(CourseTemplate template, Long teacherId) {
        return template.getOwner().getId().equals(teacherId);
    }

    private boolean isSharedWith(Long templateId, Long teacherId) {
        return shareRepository.existsByTemplateIdAndSharedWithTeacherId(templateId, teacherId);
    }
}
