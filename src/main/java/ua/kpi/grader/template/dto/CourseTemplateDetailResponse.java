package ua.kpi.grader.template.dto;

import ua.kpi.grader.template.entity.CourseTemplate;

import java.time.OffsetDateTime;
import java.util.List;

public record CourseTemplateDetailResponse(
        Long id,
        String name,
        String description,
        Long ownerId,
        String ownerFullName,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<TemplateAssignmentResponse> assignments
) {
    public static CourseTemplateDetailResponse from(CourseTemplate template,
                                                    List<TemplateAssignmentResponse> assignments) {
        var owner = template.getOwner();
        var user = owner.getUser();
        String fullName = user.getFirstName() + " " + user.getLastName();
        return new CourseTemplateDetailResponse(
                template.getId(),
                template.getName(),
                template.getDescription(),
                owner.getId(),
                fullName,
                template.getCreatedAt(),
                template.getUpdatedAt(),
                assignments
        );
    }
}
