package ua.kpi.grader.template.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ua.kpi.grader.common.dto.PageResponse;
import ua.kpi.grader.template.dto.CourseTemplateDetailResponse;
import ua.kpi.grader.template.dto.CourseTemplateResponse;
import ua.kpi.grader.template.dto.CreateCourseTemplateRequest;
import ua.kpi.grader.template.dto.UpdateCourseTemplateRequest;
import ua.kpi.grader.template.service.CourseTemplateService;

@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
public class CourseTemplateController {

    private final CourseTemplateService templateService;

    @GetMapping
    public ResponseEntity<PageResponse<CourseTemplateResponse>> listTemplates(
            @RequestParam(required = false) String query,
            @ParameterObject @PageableDefault(size = 12, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(templateService.findVisible(query, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CourseTemplateDetailResponse> getTemplate(@PathVariable Long id) {
        return ResponseEntity.ok(templateService.findById(id));
    }

    @PostMapping
    public ResponseEntity<CourseTemplateResponse> createTemplate(
            @RequestBody @Valid CreateCourseTemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(templateService.createTemplate(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CourseTemplateResponse> updateTemplate(
            @PathVariable Long id,
            @RequestBody @Valid UpdateCourseTemplateRequest request) {
        return ResponseEntity.ok(templateService.updateTemplate(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable Long id) {
        templateService.deleteTemplate(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/copy")
    public ResponseEntity<CourseTemplateResponse> copyTemplate(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(templateService.copyTemplate(id));
    }
}
