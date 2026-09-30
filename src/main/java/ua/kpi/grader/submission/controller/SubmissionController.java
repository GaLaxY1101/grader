package ua.kpi.grader.submission.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import ua.kpi.grader.storage.dto.DownloadUrl;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ua.kpi.grader.storage.dto.AttachmentSummary;
import ua.kpi.grader.submission.dto.*;
import ua.kpi.grader.submission.service.SubmissionAttachmentService;
import ua.kpi.grader.submission.service.SubmissionAttachmentService.SubmissionUploadResult;
import ua.kpi.grader.submission.service.SubmissionService;

import java.net.URI;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;
    private final SubmissionAttachmentService attachmentService;

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

    @PostMapping(
            path = "/api/assignments/{assignmentId}/submissions/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<SubmissionUploadResult> uploadSubmissionAttachments(
            @PathVariable Long assignmentId,
            @RequestPart("files") List<MultipartFile> files) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(attachmentService.upload(assignmentId, files));
    }

    @GetMapping("/api/submissions/{id}/attachments")
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN')")
    public ResponseEntity<List<AttachmentSummary>> listSubmissionAttachments(@PathVariable Long id) {
        return ResponseEntity.ok(attachmentService.list(id));
    }

    @DeleteMapping("/api/submissions/{id}/attachments/{attachmentId}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Void> deleteSubmissionAttachment(
            @PathVariable Long id,
            @PathVariable Long attachmentId) {
        attachmentService.delete(id, attachmentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/submissions/{id}/attachments/{attachmentId}/download")
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN')")
    public ResponseEntity<DownloadUrl> downloadSubmissionAttachment(
            @PathVariable Long id,
            @PathVariable Long attachmentId) {
        URI presigned = attachmentService.download(id, attachmentId);
        return ResponseEntity.ok(new DownloadUrl(presigned.toString()));
    }

    @PostMapping("/api/submissions/{id}/turn-in")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<SubmissionResponse> turnIn(@PathVariable Long id) {
        return ResponseEntity.ok(attachmentService.turnIn(id));
    }

    @PostMapping("/api/submissions/{id}/return")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<SubmissionResponse> returnSubmission(
            @PathVariable Long id,
            @RequestBody(required = false) @Valid ReturnSubmissionRequest request) {
        return ResponseEntity.ok(attachmentService.returnSubmission(id, request));
    }
}
