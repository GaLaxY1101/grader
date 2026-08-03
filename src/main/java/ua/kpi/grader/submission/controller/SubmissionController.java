package ua.kpi.grader.submission.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ua.kpi.grader.submission.dto.*;
import ua.kpi.grader.submission.service.SubmissionService;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    @PostMapping("/api/assignments/{assignmentId}/submissions")
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN')")
    public ResponseEntity<AttemptResponse> createSubmission(
            @PathVariable Long assignmentId,
            @RequestBody CreateSubmissionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(submissionService.createSubmission(assignmentId, request));
    }

    @GetMapping("/api/submissions/{id}")
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN')")
    public ResponseEntity<SubmissionResponse> getSubmission(@PathVariable Long id) {
        return ResponseEntity.ok(submissionService.findById(id));
    }

    @GetMapping("/api/submissions/{id}/status")
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN')")
    public ResponseEntity<SubmissionStatusResponse> getStatus(@PathVariable Long id) {
        return ResponseEntity.ok(submissionService.getStatus(id));
    }

    @GetMapping("/api/assignments/{assignmentId}/submissions")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<List<SubmissionResponse>> listByAssignment(@PathVariable Long assignmentId) {
        return ResponseEntity.ok(submissionService.listByAssignment(assignmentId));
    }

    @GetMapping("/api/assignments/{assignmentId}/submissions/my")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<SubmissionResponse> getMySubmission(@PathVariable Long assignmentId) {
        return submissionService.getMySubmission(assignmentId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/api/courses/{courseId}/submissions/my")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<SubmissionResponse>> listMySubmissionsInCourse(
            @PathVariable Long courseId) {
        return ResponseEntity.ok(submissionService.listMySubmissionsInCourse(courseId));
    }

    @GetMapping("/api/submissions/{submissionId}/attempts")
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN')")
    public ResponseEntity<List<AttemptResponse>> listAttempts(@PathVariable Long submissionId) {
        return ResponseEntity.ok(submissionService.listAttempts(submissionId));
    }

    @GetMapping("/api/attempts/{attemptId}/status")
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN')")
    public ResponseEntity<AttemptStatusResponse> getAttemptStatus(@PathVariable Long attemptId) {
        return ResponseEntity.ok(submissionService.getAttemptStatus(attemptId));
    }

    @PatchMapping("/api/submissions/{id}/grade")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<SubmissionResponse> updateGrade(
            @PathVariable Long id,
            @RequestBody @Valid UpdateGradeRequest request) {
        return ResponseEntity.ok(submissionService.updateGrade(id, request));
    }
}
