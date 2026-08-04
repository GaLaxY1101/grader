package ua.kpi.grader.template.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.kpi.grader.common.exception.ResourceNotFoundException;
import ua.kpi.grader.template.dto.CreateTemplateShareRequest;
import ua.kpi.grader.template.dto.TemplateShareResponse;
import ua.kpi.grader.template.entity.CourseTemplate;
import ua.kpi.grader.template.entity.TemplateShare;
import ua.kpi.grader.template.repository.TemplateShareRepository;
import ua.kpi.grader.user.entity.Teacher;
import ua.kpi.grader.user.repository.TeacherRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TemplateShareServiceImpl implements TemplateShareService {

    private final TemplateShareRepository shareRepository;
    private final TeacherRepository teacherRepository;
    private final TemplateAccessService access;

    @Override
    @Transactional(readOnly = true)
    public List<TemplateShareResponse> findAllByTemplate(Long templateId) {
        access.requireEdit(templateId);
        return shareRepository.findAllByTemplateIdWithTeacherUser(templateId).stream()
                .map(TemplateShareResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public TemplateShareResponse shareTemplate(Long templateId, CreateTemplateShareRequest request) {
        CourseTemplate template = access.requireEdit(templateId);
        Teacher targetTeacher = teacherRepository.findById(request.teacherId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Teacher not found with id: " + request.teacherId()));

        if (template.getOwner().getId().equals(targetTeacher.getId())) {
            throw new IllegalStateException("Cannot share a template with its owner");
        }
        if (shareRepository.existsByTemplateIdAndSharedWithTeacherId(templateId, targetTeacher.getId())) {
            throw new IllegalStateException(
                    "Template " + templateId + " is already shared with teacher " + targetTeacher.getId());
        }

        Teacher sharedBy = access.currentTeacher();
        TemplateShare share = TemplateShare.builder()
                .template(template)
                .sharedWithTeacher(targetTeacher)
                .sharedByTeacher(sharedBy)
                .build();
        return TemplateShareResponse.from(shareRepository.save(share));
    }

    @Override
    @Transactional
    public void unshareTemplate(Long templateId, Long teacherId) {
        access.requireEdit(templateId);
        TemplateShare share = shareRepository
                .findByTemplateIdAndSharedWithTeacherId(templateId, teacherId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Template " + templateId + " is not shared with teacher " + teacherId));
        shareRepository.delete(share);
    }
}
