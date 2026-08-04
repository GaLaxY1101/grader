package ua.kpi.grader.template.dto;

import ua.kpi.grader.template.entity.CourseTemplate;

import java.time.OffsetDateTime;

public record CourseTemplateResponse(
        Long id,
        String name,
        String description,
        Long ownerId,
        String ownerFullName,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static CourseTemplateResponse from(CourseTemplate template) {
        var owner = template.getOwner();
        var user = owner.getUser();
        String fullName = user.getFirstName() + " " + user.getLastName();
        return new CourseTemplateResponse(
                template.getId(),
                template.getName(),
                template.getDescription(),
                owner.getId(),
                fullName,
                template.getCreatedAt(),
                template.getUpdatedAt()
        );
    }
}
