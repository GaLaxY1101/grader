package ua.kpi.grader.gitlab;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import ua.kpi.grader.gitlab.client.GitLabApiClient;
import ua.kpi.grader.gitlab.config.GitLabProperties;

/**
 * Ensures required GitLab resources exist on application startup.
 * Runs automatically after the Spring context is ready.
 * All operations are idempotent — safe to run on every boot.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GitLabInitializer implements ApplicationRunner {

    private final GitLabApiClient gitLabApiClient;
    private final GitLabProperties gitLabProperties;

    @Override
    public void run(ApplicationArguments args) {
        if (gitLabProperties.token() == null || gitLabProperties.token().isBlank()) {
            log.warn("GITLAB_TOKEN is not configured — skipping GitLab initialization");
            return;
        }

        log.info("Running GitLab initialization...");
        try {
            gitLabApiClient.allowLocalWebhooks();
            Integer groupId = gitLabApiClient.getOrCreateGroup();
            log.info("GitLab ready — group '{}' id={}", gitLabProperties.groupName(), groupId);
        } catch (Exception e) {
            log.error("GitLab initialization failed: {}", e.getMessage());
        }
    }
}
