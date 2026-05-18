package ua.kpi.grader.group.dto;

import ua.kpi.grader.group.entity.AcademicGroup;

import java.time.OffsetDateTime;

public record GroupResponse(
        Long id,
        String code,
        String faculty,
        String speciality,
        Integer yearOfCreation,
        boolean isActive,
        OffsetDateTime createdAt
) {
    public static GroupResponse from(AcademicGroup group) {
        return new GroupResponse(
                group.getId(),
                group.getCode(),
                group.getFaculty(),
                group.getSpeciality(),
                group.getYearOfCreation(),
                group.isActive(),
                group.getCreatedAt()
        );
    }
}
