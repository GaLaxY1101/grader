package ua.kpi.grader.course.dto;

import ua.kpi.grader.course.entity.CourseTeacher;

public record CourseTeacherResponse(
        Long teacherId,
        String firstName,
        String lastName,
        String email,
        String role
) {
    public static CourseTeacherResponse from(CourseTeacher ct) {
        return new CourseTeacherResponse(
                ct.getTeacher().getId(),
                ct.getTeacher().getUser().getFirstName(),
                ct.getTeacher().getUser().getLastName(),
                ct.getTeacher().getUser().getEmail(),
                ct.getRole()
        );
    }
}
