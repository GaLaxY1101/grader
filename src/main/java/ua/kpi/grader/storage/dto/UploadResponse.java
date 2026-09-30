package ua.kpi.grader.storage.dto;

import java.util.List;

public record UploadResponse(
        List<AttachmentSummary> uploaded,
        List<UploadFailure> failed
) {}
