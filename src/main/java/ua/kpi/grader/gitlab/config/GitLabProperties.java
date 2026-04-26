package ua.kpi.grader.gitlab.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gitlab")
public record GitLabProperties(
        String baseUrl,
        String token,
        String groupName,
        String webhookSecret,
        String runnerToken
) {}
