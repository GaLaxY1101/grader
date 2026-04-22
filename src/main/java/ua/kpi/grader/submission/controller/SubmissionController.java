package ua.kpi.grader.submission.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ua.kpi.grader.submission.dto.CreateSubmissionRequest;
import ua.kpi.grader.submission.dto.SubmissionResponse;
import ua.kpi.grader.submission.dto.SubmissionStatusResponse;
import ua.kpi.grader.submission.service.SubmissionService;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    @PostMapping("/api/assignments/{assignmentId}/submissions")
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN')")
    public ResponseEntity<SubmissionResponse> createSubmission(
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
    public ResponseEntity<SubmissionResponse> getMyLatest(@PathVariable Long assignmentId) {
        return submissionService.getMyLatestSubmission(assignmentId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }
}
