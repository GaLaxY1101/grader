package ua.kpi.grader.template.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ua.kpi.grader.template.dto.CreateTemplateShareRequest;
import ua.kpi.grader.template.dto.TemplateShareResponse;
import ua.kpi.grader.template.service.TemplateShareService;

import java.util.List;

@RestController
@RequestMapping("/api/templates/{templateId}/shares")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
public class TemplateShareController {

    private final TemplateShareService shareService;

    @GetMapping
    public ResponseEntity<List<TemplateShareResponse>> listShares(@PathVariable Long templateId) {
        return ResponseEntity.ok(shareService.findAllByTemplate(templateId));
    }

    @PostMapping
    public ResponseEntity<TemplateShareResponse> shareTemplate(
            @PathVariable Long templateId,
            @RequestBody @Valid CreateTemplateShareRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(shareService.shareTemplate(templateId, request));
    }

    @DeleteMapping("/{teacherId}")
    public ResponseEntity<Void> unshareTemplate(
            @PathVariable Long templateId,
            @PathVariable Long teacherId) {
        shareService.unshareTemplate(templateId, teacherId);
        return ResponseEntity.noContent().build();
    }
}
