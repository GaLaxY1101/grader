package ua.kpi.grader.testgen.dto;

import ua.kpi.grader.course.entity.Language;
import ua.kpi.grader.testgen.entity.JobStatus;
import ua.kpi.grader.testgen.entity.TestGenerationJob;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * State of a test generation job with its iterations so far.
 *
 * @param finalTestContent validated test file; set when {@code status} is SUCCEEDED
 * @param errorMessage     reason for failure; set when {@code status} is FAILED
 */
public record TestGenerationJobResponse(
        Long id,
        JobStatus status,
        Language language,
        String model,
        Long assignmentId,
        String finalTestContent,
        String errorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime finishedAt,
        List<IterationResponse> iterations
) {
    public static TestGenerationJobResponse from(TestGenerationJob job, List<IterationResponse> iterations) {
        return new TestGenerationJobResponse(
                job.getId(),
                job.getStatus(),
                job.getLanguage(),
                job.getModel(),
                job.getAssignment() != null ? job.getAssignment().getId() : null,
                job.getFinalTestContent(),
                job.getErrorMessage(),
                job.getCreatedAt(),
                job.getFinishedAt(),
                iterations);
    }
}
