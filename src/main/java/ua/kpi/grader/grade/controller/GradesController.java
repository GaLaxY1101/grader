package ua.kpi.grader.grade.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ua.kpi.grader.course.dto.CourseGradesResponse;
import ua.kpi.grader.grade.service.GradesService;

@RestController
@RequestMapping("/api/courses/{courseId}/grades")
@RequiredArgsConstructor
public class GradesController {

    private static final MediaType MEDIA_XLSX = MediaType
            .parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final MediaType MEDIA_CSV = MediaType.parseMediaType("text/csv; charset=UTF-8");

    private final GradesService gradesService;

    @GetMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<CourseGradesResponse> getCourseGrades(@PathVariable Long courseId) {
        return ResponseEntity.ok(gradesService.getCourseGrades(courseId));
    }

    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<byte[]> exportCourseGrades(
            @PathVariable Long courseId,
            @RequestParam(name = "format", defaultValue = "csv") String format) {

        String fmt = format.toLowerCase();
        return switch (fmt) {
            case "xlsx" -> streamed(
                    gradesService.exportCourseGradesXlsx(courseId),
                    MEDIA_XLSX,
                    "grades-course-%d.xlsx".formatted(courseId));
            case "csv" -> streamed(
                    gradesService.exportCourseGradesCsv(courseId),
                    MEDIA_CSV,
                    "grades-course-%d.csv".formatted(courseId));
            default -> throw new IllegalArgumentException(
                    "Unsupported export format: " + format + " (allowed: csv, xlsx)");
        };
    }

    private static ResponseEntity<byte[]> streamed(byte[] body, MediaType type, String filename) {
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(body);
    }
}
