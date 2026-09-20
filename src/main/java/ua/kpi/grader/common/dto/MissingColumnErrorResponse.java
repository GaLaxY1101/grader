package ua.kpi.grader.common.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Error response for {@link ua.kpi.grader.common.exception.MissingColumnException}.
 * Extends the standard error shape with the missing header name and the list of
 * headers actually detected in the uploaded file.
 */
public record MissingColumnErrorResponse(
        int status,
        String title,
        String detail,
        OffsetDateTime timestamp,
        String missingColumn,
        List<String> detectedHeaders
) {}
