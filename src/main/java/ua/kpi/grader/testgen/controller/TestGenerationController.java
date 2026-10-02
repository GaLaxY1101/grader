package ua.kpi.grader.testgen.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ua.kpi.grader.testgen.dto.StartTestGenerationRequest;
import ua.kpi.grader.testgen.dto.StartTestGenerationResponse;
import ua.kpi.grader.testgen.dto.TestGenerationJobResponse;
import ua.kpi.grader.testgen.service.TestGenerationService;

@RestController
@RequestMapping("/api/test-generation")
@RequiredArgsConstructor
public class TestGenerationController {

    private final TestGenerationService testGenerationService;

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<StartTestGenerationResponse> start(@RequestBody @Valid StartTestGenerationRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new StartTestGenerationResponse(testGenerationService.startJob(request)));
    }

    @GetMapping("/{jobId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<TestGenerationJobResponse> getJob(@PathVariable Long jobId) {
        return ResponseEntity.ok(testGenerationService.getJob(jobId));
    }
}
