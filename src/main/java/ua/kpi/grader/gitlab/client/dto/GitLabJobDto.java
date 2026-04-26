package ua.kpi.grader.gitlab.client.dto;

public record GitLabJobDto(
        Integer id,
        String name,
        String status,
        Integer stage
) {}
