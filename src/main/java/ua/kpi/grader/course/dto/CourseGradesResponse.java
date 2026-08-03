package ua.kpi.grader.course.dto;

import ua.kpi.grader.submission.entity.SubmissionStatus;

import java.util.List;

public record CourseGradesResponse(
        Long courseId,
        String courseName,
        List<AssignmentGradeSummary> assignments,
        List<StudentGradesRow> students
) {
    public record AssignmentGradeSummary(
            Long id,
            String title,
            Integer maxScore
    ) {}

    public record StudentGradeCell(
            Long assignmentId,
            Integer grade,
            SubmissionStatus status
    ) {}

    public record StudentGradesRow(
            Long studentId,
            String email,
            String firstName,
            String lastName,
            List<StudentGradeCell> grades,
            Integer total,
            Integer maxTotal
    ) {}
}
