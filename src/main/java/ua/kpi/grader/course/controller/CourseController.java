package ua.kpi.grader.course.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ua.kpi.grader.common.dto.PageResponse;
import ua.kpi.grader.course.dto.*;
import ua.kpi.grader.course.service.CourseService;

import java.util.List;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @GetMapping
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN')")
    public ResponseEntity<PageResponse<CourseResponse>> listCourses(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Long groupId,
            @RequestParam(defaultValue = "true") boolean isActive,
            @ParameterObject @PageableDefault(size = 12, sort = "createdAt",
                    direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(courseService.findAll(query, groupId, isActive, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN')")
    public ResponseEntity<CourseDetailResponse> getCourse(@PathVariable Long id) {
        return ResponseEntity.ok(courseService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<CourseResponse> createCourse(
            @RequestBody @Valid CreateCourseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(courseService.createCourse(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<CourseResponse> updateCourse(
            @PathVariable Long id,
            @RequestBody @Valid UpdateCourseRequest request) {
        return ResponseEntity.ok(courseService.updateCourse(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<Void> deactivateCourse(@PathVariable Long id) {
        courseService.deactivateCourse(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<Void> activateCourse(@PathVariable Long id) {
        courseService.activateCourse(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/students/{studentId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<EnrolledStudentResponse> enrollStudent(
            @PathVariable Long id,
            @PathVariable Long studentId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(courseService.enrollStudent(id, studentId));
    }

    @DeleteMapping("/{id}/students/{studentId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<Void> unenrollStudent(
            @PathVariable Long id,
            @PathVariable Long studentId) {
        courseService.unenrollStudent(id, studentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/groups/{groupId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<List<EnrolledStudentResponse>> enrollGroup(
            @PathVariable Long id,
            @PathVariable Long groupId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(courseService.enrollGroup(id, groupId));
    }

    @DeleteMapping("/{id}/groups/{groupId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<List<Long>> unenrollGroup(
            @PathVariable Long id,
            @PathVariable Long groupId) {
        return ResponseEntity.ok(courseService.unenrollGroup(id, groupId));
    }

    @GetMapping("/{id}/students")
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN')")
    public ResponseEntity<List<EnrolledStudentResponse>> listStudents(@PathVariable Long id) {
        return ResponseEntity.ok(courseService.findStudents(id));
    }

    @PostMapping("/{id}/teachers/{teacherId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<CourseTeacherResponse> addTeacher(
            @PathVariable Long id,
            @PathVariable Long teacherId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(courseService.addTeacher(id, teacherId));
    }

    @DeleteMapping("/{id}/teachers/{teacherId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<Void> removeTeacher(
            @PathVariable Long id,
            @PathVariable Long teacherId) {
        courseService.removeTeacher(id, teacherId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/teachers")
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN')")
    public ResponseEntity<List<CourseTeacherResponse>> listTeachers(@PathVariable Long id) {
        return ResponseEntity.ok(courseService.findTeachers(id));
    }
}
