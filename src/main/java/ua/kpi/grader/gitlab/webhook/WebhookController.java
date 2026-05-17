package ua.kpi.grader.gitlab.webhook;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ua.kpi.grader.gitlab.client.dto.GitLabWebhookPayload;
import ua.kpi.grader.gitlab.config.GitLabProperties;
import ua.kpi.grader.submission.service.SubmissionService;

@Slf4j
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final SubmissionService submissionService;
    private final GitLabProperties gitLabProperties;

    /**
     * Receives GitLab pipeline events and applies the result to the matching submission.
     * Ignores non-terminal statuses (pending, running, created) — only processes
     * success, failed, and canceled/other terminal states.
     * The X-Gitlab-Token header is verified against the configured webhook secret.
     */
    @PostMapping("/gitlab")
    public ResponseEntity<Void> handleGitLabWebhook(
            @RequestHeader("X-Gitlab-Token") String token,
            @RequestBody GitLabWebhookPayload payload
    ) {
        if (!gitLabProperties.webhookSecret().equals(token)) {
            log.warn("Received webhook with invalid token");
            return ResponseEntity.status(401).build();
        }

        if (!"pipeline".equals(payload.objectKind())) {
            return ResponseEntity.ok().build();
        }

        String status = payload.objectAttributes().status();
        if ("pending".equals(status) || "running".equals(status) || "created".equals(status)) {
            return ResponseEntity.ok().build();
        }

        log.info("GitLab webhook: project={}, pipeline={}, status={}",
                payload.project().id(), payload.objectAttributes().id(), status);

        submissionService.applyGitLabResult(
                payload.project().id().longValue(),
                payload.objectAttributes().id().longValue(),
                status
        );

        return ResponseEntity.ok().build();
    }
}
