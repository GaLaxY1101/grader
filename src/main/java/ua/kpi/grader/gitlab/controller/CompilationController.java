package ua.kpi.grader.gitlab.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ua.kpi.grader.common.exception.ResourceNotFoundException;
import ua.kpi.grader.course.entity.ProgrammingTask;
import ua.kpi.grader.course.repository.ProgrammingTaskRepository;
import ua.kpi.grader.gitlab.dto.CompileRequest;
import ua.kpi.grader.gitlab.dto.CompileResponse;
import ua.kpi.grader.gitlab.service.CompilationResult;
import ua.kpi.grader.gitlab.service.CompilationService;

@RestController
@RequiredArgsConstructor
public class CompilationController {

    private final CompilationService compilationService;
    private final ProgrammingTaskRepository programmingTaskRepository;

    /**
     * Validates that the provided code compiles.
     * Used by teachers when creating assignments (validates function signature + test file).
     */
    @PostMapping("/api/compile/validate")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<CompileResponse> validateCompilation(@Valid @RequestBody CompileRequest request) {
        if (request.language() == null) {
            throw new IllegalArgumentException("language is required");
        }
        CompilationResult result;
        if (request.testFileContent() != null && !request.testFileContent().isBlank()) {
            result = compilationService.compileSolutionWithTests(
                    request.solutionCode(), request.testFileContent(), request.language());
        } else {
            result = compilationService.compileSolution(request.solutionCode(), request.language());
        }
        return ResponseEntity.ok(new CompileResponse(result.success(), result.output()));
    }

    /**
     * Validates that student code compiles against the assignment's test file.
     * Called before creating a submission to give early feedback.
     */
    @PostMapping("/api/assignments/{assignmentId}/compile")
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN')")
    public ResponseEntity<CompileResponse> validateSubmissionCompilation(
            @PathVariable Long assignmentId,
            @Valid @RequestBody CompileRequest request) {
        ProgrammingTask task = programmingTaskRepository.findByAssignmentId(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Programming task not found for assignment " + assignmentId));

        CompilationResult result = compilationService.compileSolutionWithTests(
                request.solutionCode(), task.getTestFileContent(), task.getLanguage());
        return ResponseEntity.ok(new CompileResponse(result.success(), result.output()));
    }
}
