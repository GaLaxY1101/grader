package ua.kpi.grader.grade.service;

import ua.kpi.grader.course.dto.CourseGradesResponse;

public interface GradesService {

    record ExportedGradebook(String filename, byte[] bytes) {}

    /**
     * Assembles the full gradebook for a course: assignments across columns,
     * active enrollees down rows, per-cell grade + submission status, and
     * per-row totals (sum of non-null grades vs. sum of maxScore).
     *
     * @param courseId the course id
     * @return the assembled CourseGradesResponse
     */
    CourseGradesResponse getCourseGrades(Long courseId);

    /**
     * Renders the course gradebook to an XLSX workbook (Apache POI) for
     * streaming to the client.
     *
     * @param courseId the course id
     * @return XLSX payload
     */
    ExportedGradebook exportCourseGradesXlsx(Long courseId);
}
