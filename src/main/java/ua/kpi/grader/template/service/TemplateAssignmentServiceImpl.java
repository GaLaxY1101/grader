package ua.kpi.grader.template.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.kpi.grader.common.exception.ResourceNotFoundException;
import ua.kpi.grader.course.dto.ProgrammingTaskDetails;
import ua.kpi.grader.template.dto.CreateTemplateAssignmentRequest;
import ua.kpi.grader.template.dto.TemplateAssignmentResponse;
import ua.kpi.grader.template.dto.UpdateTemplateAssignmentRequest;
import ua.kpi.grader.template.entity.CourseTemplate;
import ua.kpi.grader.template.entity.TemplateAssignment;
import ua.kpi.grader.template.entity.TemplateProgrammingTask;
import ua.kpi.grader.template.mapper.TemplateContentMapper;
import ua.kpi.grader.template.repository.TemplateAssignmentRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TemplateAssignmentServiceImpl implements TemplateAssignmentService {

    private final TemplateAssignmentRepository assignmentRepository;
    private final TemplateAccessService access;
    private final TemplateContentMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<TemplateAssignmentResponse> findAllByTemplate(Long templateId) {
        access.requireView(templateId);
        return assignmentRepository.findAllByTemplateId(templateId).stream()
                .map(TemplateAssignmentResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TemplateAssignmentResponse findById(Long id) {
        TemplateAssignment assignment = findOrThrow(id);
        access.requireView(assignment.getTemplate().getId());
        return TemplateAssignmentResponse.from(assignment);
    }

    @Override
    @Transactional
    public TemplateAssignmentResponse createAssignment(Long templateId,
                                                       CreateTemplateAssignmentRequest request) {
        CourseTemplate template = access.requireEdit(templateId);
        TemplateAssignment assignment = TemplateAssignment.builder()
                .template(template)
                .title(request.title())
                .description(request.description())
                .maxScore(request.maxScore() != null ? request.maxScore() : 100)
                .build();
        if (request.programmingTask() != null) {
            assignment.setProgrammingTask(mapper.buildProgrammingTask(assignment, request.programmingTask()));
        }
        return TemplateAssignmentResponse.from(assignmentRepository.save(assignment));
    }

    @Override
    @Transactional
    public TemplateAssignmentResponse updateAssignment(Long id, UpdateTemplateAssignmentRequest request) {
        TemplateAssignment assignment = findOrThrow(id);
        access.requireEdit(assignment.getTemplate().getId());

        assignment.update(request.title(), request.description(), request.maxScore());

        ProgrammingTaskDetails incoming = request.programmingTask();
        TemplateProgrammingTask existing = assignment.getProgrammingTask();

        if (incoming != null && existing == null) {
            assignment.setProgrammingTask(mapper.buildProgrammingTask(assignment, incoming));
        } else if (incoming != null) {
            mapper.updateProgrammingTask(existing, incoming);
        } else if (existing != null) {
            assignment.setProgrammingTask(null);
        }

        return TemplateAssignmentResponse.from(assignment);
    }

    @Override
    @Transactional
    public void deleteAssignment(Long id) {
        TemplateAssignment assignment = findOrThrow(id);
        access.requireEdit(assignment.getTemplate().getId());
        assignmentRepository.delete(assignment);
    }

    private TemplateAssignment findOrThrow(Long id) {
        return assignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Template assignment not found with id: " + id));
    }
}
