package ua.kpi.grader.template.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ua.kpi.grader.storage.dto.AttachmentSummary;
import ua.kpi.grader.storage.dto.DownloadUrl;
import ua.kpi.grader.storage.dto.UploadResponse;
import ua.kpi.grader.template.dto.CreateTemplateAssignmentRequest;
import ua.kpi.grader.template.dto.TemplateAssignmentResponse;
import ua.kpi.grader.template.dto.UpdateTemplateAssignmentRequest;
import ua.kpi.grader.template.service.TemplateAssignmentAttachmentService;
import ua.kpi.grader.template.service.TemplateAssignmentService;

import java.net.URI;
import java.util.List;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
public class TemplateAssignmentController {

    private final TemplateAssignmentService assignmentService;
    private final TemplateAssignmentAttachmentService attachmentService;

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

    @PostMapping(
            path = "/api/template-assignments/{id}/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> uploadAttachments(
            @PathVariable Long id,
            @RequestPart("files") List<MultipartFile> files) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(attachmentService.upload(id, files));
    }

    @GetMapping("/api/template-assignments/{id}/attachments")
    public ResponseEntity<List<AttachmentSummary>> listAttachments(@PathVariable Long id) {
        return ResponseEntity.ok(attachmentService.list(id));
    }

    @DeleteMapping("/api/template-assignments/{id}/attachments/{attachmentId}")
    public ResponseEntity<Void> deleteAttachment(
            @PathVariable Long id,
            @PathVariable Long attachmentId) {
        attachmentService.delete(id, attachmentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/template-assignments/{id}/attachments/{attachmentId}/download")
    public ResponseEntity<DownloadUrl> downloadAttachment(
            @PathVariable Long id,
            @PathVariable Long attachmentId) {
        URI presigned = attachmentService.download(id, attachmentId);
        return ResponseEntity.ok(new DownloadUrl(presigned.toString()));
    }
}
