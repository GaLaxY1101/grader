package ua.kpi.grader.template.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.kpi.grader.common.dto.PageResponse;
import ua.kpi.grader.security.CurrentUser;
import ua.kpi.grader.template.dto.CourseTemplateDetailResponse;
import ua.kpi.grader.template.dto.CourseTemplateResponse;
import ua.kpi.grader.template.dto.CreateCourseTemplateRequest;
import ua.kpi.grader.template.dto.TemplateAssignmentResponse;
import ua.kpi.grader.template.dto.UpdateCourseTemplateRequest;
import ua.kpi.grader.template.entity.CourseTemplate;
import ua.kpi.grader.template.entity.TemplateAssignment;
import ua.kpi.grader.template.mapper.TemplateContentMapper;
import ua.kpi.grader.template.repository.CourseTemplateRepository;
import ua.kpi.grader.template.repository.TemplateAssignmentRepository;
import ua.kpi.grader.user.entity.Teacher;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CourseTemplateServiceImpl implements CourseTemplateService {

    private static final String ROLE_ADMIN = "ADMIN";

    private final CourseTemplateRepository templateRepository;
    private final TemplateAssignmentRepository assignmentRepository;
    private final TemplateAccessService access;
    private final TemplateContentMapper mapper;
    private final CurrentUser currentUser;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CourseTemplateResponse> findVisible(String query, Pageable pageable) {
        String normalized = normalizeQuery(query);
        Page<CourseTemplate> page = currentUser.hasRole(ROLE_ADMIN)
                ? templateRepository.findAllFiltered(normalized, pageable)
                : templateRepository.findVisibleTo(access.currentTeacher().getId(), normalized, pageable);
        return PageResponse.from(page.map(CourseTemplateResponse::from));
    }

    @Override
    @Transactional(readOnly = true)
    public CourseTemplateDetailResponse findById(Long id) {
        CourseTemplate template = access.requireView(id);
        List<TemplateAssignmentResponse> assignments = assignmentRepository
                .findAllByTemplateId(id).stream()
                .map(TemplateAssignmentResponse::from)
                .toList();
        return CourseTemplateDetailResponse.from(template, assignments);
    }

    @Override
    @Transactional
    public CourseTemplateResponse createTemplate(CreateCourseTemplateRequest request) {
        Teacher owner = access.currentTeacher();
        CourseTemplate template = CourseTemplate.builder()
                .name(request.name())
                .description(request.description())
                .owner(owner)
                .build();
        return CourseTemplateResponse.from(templateRepository.save(template));
    }

    @Override
    @Transactional
    public CourseTemplateResponse updateTemplate(Long id, UpdateCourseTemplateRequest request) {
        CourseTemplate template = access.requireEdit(id);
        template.update(request.name(), request.description());
        return CourseTemplateResponse.from(template);
    }

    @Override
    @Transactional
    public void deleteTemplate(Long id) {
        CourseTemplate template = access.requireEdit(id);
        templateRepository.delete(template);
    }

    @Override
    @Transactional
    public CourseTemplateResponse copyTemplate(Long id) {
        CourseTemplate source = access.requireView(id);
        Teacher newOwner = access.currentTeacher();

        CourseTemplate copy = CourseTemplate.builder()
                .name(source.getName() + " (copy)")
                .description(source.getDescription())
                .owner(newOwner)
                .build();
        CourseTemplate saved = templateRepository.save(copy);

        List<TemplateAssignment> sourceAssignments = assignmentRepository.findAllByTemplateId(id);
        for (TemplateAssignment src : sourceAssignments) {
            assignmentRepository.save(mapper.cloneAssignment(src, saved));
        }
        return CourseTemplateResponse.from(saved);
    }

    private static String normalizeQuery(String query) {
        if (query == null) return null;
        String trimmed = query.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase();
    }
}
