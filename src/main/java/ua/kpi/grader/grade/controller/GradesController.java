package ua.kpi.grader.grade.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ua.kpi.grader.course.dto.CourseGradesResponse;
import ua.kpi.grader.grade.service.GradesService;

@RestController
@RequestMapping("/api/courses/{courseId}/grades")
@RequiredArgsConstructor
public class GradesController {

    private static final MediaType MEDIA_XLSX = MediaType
            .parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final GradesService gradesService;

    @GetMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<CourseGradesResponse> getCourseGrades(@PathVariable Long courseId) {
        return ResponseEntity.ok(gradesService.getCourseGrades(courseId));
    }

    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<byte[]> exportCourseGrades(@PathVariable Long courseId) {
        GradesService.ExportedGradebook file = gradesService.exportCourseGradesXlsx(courseId);
        return ResponseEntity.ok()
                .contentType(MEDIA_XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + file.filename() + "\"")
                .body(file.bytes());
    }
}
