package ua.kpi.grader.submission.dto;

import jakarta.validation.constraints.Size;

public record ReturnSubmissionRequest(
        @Size(max = 2000) String comment
) {}
