package ua.kpi.grader.course.dto;

import ua.kpi.grader.course.entity.Course;

import java.time.OffsetDateTime;

public record CourseResponse(
        Long id,
        String name,
        String description,
        Integer academicYear,
        Integer semester,
        boolean isActive,
        OffsetDateTime createdAt
) {
    public static CourseResponse from(Course course) {
        return new CourseResponse(
                course.getId(),
                course.getName(),
                course.getDescription(),
                course.getAcademicYear(),
                course.getSemester(),
                course.isActive(),
                course.getCreatedAt()
        );
    }
}
