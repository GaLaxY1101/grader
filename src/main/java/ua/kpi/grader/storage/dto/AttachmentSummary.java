package ua.kpi.grader.storage.dto;

import java.time.OffsetDateTime;

public record AttachmentSummary(
        Long id,
        String filename,
        String contentType,
        long sizeBytes,
        OffsetDateTime uploadedAt,
        Long uploadedById
) {}
