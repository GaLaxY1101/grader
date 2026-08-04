package ua.kpi.grader.template.dto;

import ua.kpi.grader.template.entity.TemplateShare;

import java.time.OffsetDateTime;

public record TemplateShareResponse(
        Long id,
        Long templateId,
        Long teacherId,
        String teacherEmail,
        String teacherFullName,
        Long sharedByTeacherId,
        OffsetDateTime createdAt
) {
    public static TemplateShareResponse from(TemplateShare share) {
        var teacher = share.getSharedWithTeacher();
        var user = teacher.getUser();
        String fullName = user.getFirstName() + " " + user.getLastName();
        return new TemplateShareResponse(
                share.getId(),
                share.getTemplate().getId(),
                teacher.getId(),
                user.getEmail(),
                fullName,
                share.getSharedByTeacher().getId(),
                share.getCreatedAt()
        );
    }
}
