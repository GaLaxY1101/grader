package ua.kpi.grader.template.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ua.kpi.grader.template.dto.CreateTemplateAssignmentRequest;
import ua.kpi.grader.template.dto.TemplateAssignmentResponse;
import ua.kpi.grader.template.dto.UpdateTemplateAssignmentRequest;
import ua.kpi.grader.template.service.TemplateAssignmentService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
public class TemplateAssignmentController {

    private final TemplateAssignmentService assignmentService;

    @GetMapping("/api/templates/{templateId}/assignments")
    public ResponseEntity<List<TemplateAssignmentResponse>> listAssignments(
            @PathVariable Long templateId) {
        return ResponseEntity.ok(assignmentService.findAllByTemplate(templateId));
    }

    @PostMapping("/api/templates/{templateId}/assignments")
    public ResponseEntity<TemplateAssignmentResponse> createAssignment(
            @PathVariable Long templateId,
            @RequestBody @Valid CreateTemplateAssignmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(assignmentService.createAssignment(templateId, request));
    }

    @GetMapping("/api/template-assignments/{id}")
    public ResponseEntity<TemplateAssignmentResponse> getAssignment(@PathVariable Long id) {
        return ResponseEntity.ok(assignmentService.findById(id));
    }

    @PutMapping("/api/template-assignments/{id}")
    public ResponseEntity<TemplateAssignmentResponse> updateAssignment(
            @PathVariable Long id,
            @RequestBody @Valid UpdateTemplateAssignmentRequest request) {
        return ResponseEntity.ok(assignmentService.updateAssignment(id, request));
    }

    @DeleteMapping("/api/template-assignments/{id}")
    public ResponseEntity<Void> deleteAssignment(@PathVariable Long id) {
        assignmentService.deleteAssignment(id);
        return ResponseEntity.noContent().build();
    }
}
