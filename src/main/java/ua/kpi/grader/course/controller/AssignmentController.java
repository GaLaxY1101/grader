package ua.kpi.grader.course.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ua.kpi.grader.course.dto.AssignmentResponse;
import ua.kpi.grader.course.dto.CreateAssignmentRequest;
import ua.kpi.grader.course.dto.UpdateAssignmentRequest;
import ua.kpi.grader.course.service.AssignmentService;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class AssignmentController {

    private final AssignmentService assignmentService;

    @PostMapping("/api/courses/{courseId}/assignments")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<AssignmentResponse> createAssignment(
            @PathVariable Long courseId,
            @RequestBody @Valid CreateAssignmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(assignmentService.createAssignment(courseId, request));
    }

    @GetMapping("/api/courses/{courseId}/assignments")
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN')")
    public ResponseEntity<List<AssignmentResponse>> listAssignments(@PathVariable Long courseId) {
        return ResponseEntity.ok(assignmentService.findAllByCourse(courseId));
    }

    @GetMapping("/api/assignments/{id}")
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN')")
    public ResponseEntity<AssignmentResponse> getAssignment(@PathVariable Long id) {
        return ResponseEntity.ok(assignmentService.findById(id));
    }

    @PutMapping("/api/assignments/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<AssignmentResponse> updateAssignment(
            @PathVariable Long id,
            @RequestBody @Valid UpdateAssignmentRequest request) {
        return ResponseEntity.ok(assignmentService.updateAssignment(id, request));
    }

    @DeleteMapping("/api/assignments/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<Void> deactivateAssignment(@PathVariable Long id) {
        assignmentService.deactivateAssignment(id);
        return ResponseEntity.noContent().build();
    }
}
