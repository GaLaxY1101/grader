package ua.kpi.grader.testgen.dto;

/**
 * Id of the started job; poll {@code GET /api/test-generation/{jobId}} for progress.
 */
public record StartTestGenerationResponse(Long jobId) {}
