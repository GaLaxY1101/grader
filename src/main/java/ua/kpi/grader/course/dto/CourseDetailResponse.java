package ua.kpi.grader.course.dto;

import ua.kpi.grader.course.entity.Course;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record CourseDetailResponse(
        Long id,
        String name,
        String description,
        Integer academicYear,
        Integer semester,
        LocalDate startDate,
        LocalDate endDate,
        boolean isActive,
        OffsetDateTime createdAt,
        List<CourseTeacherResponse> teachers,
        List<EnrolledStudentResponse> students,
        List<AssignmentResponse> assignments
) {
    public static CourseDetailResponse from(Course course,
                                            List<CourseTeacherResponse> teachers,
                                            List<EnrolledStudentResponse> students,
                                            List<AssignmentResponse> assignments) {
        return new CourseDetailResponse(
                course.getId(),
                course.getName(),
                course.getDescription(),
                course.getAcademicYear(),
                course.getSemester(),
                course.getStartDate(),
                course.getEndDate(),
                course.isActive(),
                course.getCreatedAt(),
                teachers,
                students,
                assignments
        );
    }
}
