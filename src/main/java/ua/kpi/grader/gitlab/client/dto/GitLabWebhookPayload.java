package ua.kpi.grader.gitlab.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GitLabWebhookPayload(
        @JsonProperty("object_kind") String objectKind,
        @JsonProperty("object_attributes") PipelineAttributes objectAttributes,
        Project project
) {

    public record PipelineAttributes(
            Integer id,
            String status,
            String ref
    ) {}

    public record Project(
            Integer id,
            String name
    ) {}
}
