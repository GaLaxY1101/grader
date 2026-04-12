package ua.kpi.grader.common.dto;

import java.time.OffsetDateTime;

public record ErrorResponse(
        int status,
        String title,
        String detail,
        OffsetDateTime timestamp
) {}
