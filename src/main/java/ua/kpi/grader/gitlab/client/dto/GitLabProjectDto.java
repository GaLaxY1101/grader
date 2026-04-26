package ua.kpi.grader.gitlab.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GitLabProjectDto(
        Integer id,
        String name,
        @JsonProperty("web_url") String webUrl
) {}
