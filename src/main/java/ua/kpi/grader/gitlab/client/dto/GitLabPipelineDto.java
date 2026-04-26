package ua.kpi.grader.gitlab.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GitLabPipelineDto(
        Integer id,
        String status,
        @JsonProperty("web_url") String webUrl,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("finished_at") String finishedAt
) {}
