package ua.kpi.grader.common.dto;

import ua.kpi.grader.common.exception.InvalidImportException.RowError;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Error response for {@link ua.kpi.grader.common.exception.InvalidImportException}.
 * Extends the standard error shape with a per-row error list so the client can
 * highlight the offending rows in its editable table.
 */
public record BulkImportErrorResponse(
        int status,
        String title,
        String detail,
        OffsetDateTime timestamp,
        List<RowError> errors
) {}
