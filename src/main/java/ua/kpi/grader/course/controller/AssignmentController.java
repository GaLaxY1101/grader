package ua.kpi.grader.course.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import ua.kpi.grader.storage.dto.DownloadUrl;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ua.kpi.grader.course.dto.AssignmentResponse;
import ua.kpi.grader.course.dto.CreateAssignmentRequest;
import ua.kpi.grader.course.dto.UpdateAssignmentRequest;
import ua.kpi.grader.course.service.AssignmentAttachmentService;
import ua.kpi.grader.course.service.AssignmentService;
import ua.kpi.grader.storage.dto.AttachmentSummary;
import ua.kpi.grader.storage.dto.UploadResponse;

import java.net.URI;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class AssignmentController {

    private final AssignmentService assignmentService;
    private final AssignmentAttachmentService attachmentService;

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

    @PostMapping(
            path = "/api/assignments/{id}/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<UploadResponse> uploadAttachments(
            @PathVariable Long id,
            @RequestPart("files") List<MultipartFile> files) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(attachmentService.upload(id, files));
    }

    @GetMapping("/api/assignments/{id}/attachments")
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN')")
    public ResponseEntity<List<AttachmentSummary>> listAttachments(@PathVariable Long id) {
        return ResponseEntity.ok(attachmentService.list(id));
    }

    @DeleteMapping("/api/assignments/{id}/attachments/{attachmentId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<Void> deleteAttachment(
            @PathVariable Long id,
            @PathVariable Long attachmentId) {
        attachmentService.delete(id, attachmentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/assignments/{id}/attachments/{attachmentId}/download")
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN')")
    public ResponseEntity<DownloadUrl> downloadAttachment(
            @PathVariable Long id,
            @PathVariable Long attachmentId) {
        URI presigned = attachmentService.download(id, attachmentId);
        return ResponseEntity.ok(new DownloadUrl(presigned.toString()));
    }
}
