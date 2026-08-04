package ua.kpi.grader.gitlab.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ua.kpi.grader.gitlab.client.dto.GitLabJobDto;
import ua.kpi.grader.gitlab.client.dto.GitLabPipelineDto;
import ua.kpi.grader.gitlab.config.GitLabProperties;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class GitLabApiClient {

    private final RestClient restClient;
    private final GitLabProperties properties;

    public GitLabApiClient(GitLabProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl() + "/api/v4")
                .requestFactory(new JdkClientHttpRequestFactory())
                .defaultHeader("PRIVATE-TOKEN", properties.token())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    // ── Admin settings ────────────────────────────────────────

    /**
     * Enables local network access for webhooks in GitLab application settings.
     * Required so GitLab can POST pipeline events to host.docker.internal (Spring Boot).
     * Idempotent — safe to call on every startup.
     */
    public void allowLocalWebhooks() {
        log.info("Enabling local network webhook access in GitLab settings");
        restClient.put()
                .uri("/application/settings")
                .body(Map.of("allow_local_requests_from_web_hooks_and_services", true))
                .retrieve()
                .toBodilessEntity();
    }

    // ── Groups ────────────────────────────────────────────────

    /**
     * Get or create the root "grader" group.
     * Searches by configured group name; creates it with private visibility if absent.
     *
     * @return the GitLab group id
     */
    public Integer getOrCreateGroup() {
        log.debug("Looking up GitLab group '{}'", properties.groupName());

        List<Map<String, Object>> groups = restClient.get()
                .uri("/groups?search={name}", properties.groupName())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        if (groups != null && !groups.isEmpty()) {
            Integer id = (Integer) groups.getFirst().get("id");
            log.debug("Found existing group '{}' with id={}", properties.groupName(), id);
            return id;
        }

        log.info("Group '{}' not found, creating it", properties.groupName());
        Map<String, Object> body = Map.of(
                "name", properties.groupName(),
                "path", properties.groupName(),
                "visibility", "private"
        );
        Map<String, Object> created = restClient.post()
                .uri("/groups")
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        Integer id = (Integer) created.get("id");
        log.info("Created group '{}' with id={}", properties.groupName(), id);
        return id;
    }

    // ── Projects ──────────────────────────────────────────────

    /**
     * Create a new private GitLab project for a student-assignment pair.
     * Project name and path follow the pattern: assignment-{assignmentId}-student-{studentId}.
     * The project is reused across attempts.
     *
     * @param assignmentId the assignment id
     * @param studentId    the student id
     * @param groupId      the GitLab group (namespace) to create the project under
     * @return the created GitLab project id
     */
    public Integer createProject(Long assignmentId, Long studentId, Integer groupId) {
        String name = "assignment-%d-student-%d".formatted(assignmentId, studentId);
        log.info("Creating GitLab project '{}' under group id={}", name, groupId);

        Map<String, Object> body = Map.of(
                "name", name,
                "path", name,
                "namespace_id", groupId,
                "visibility", "private",
                "initialize_with_readme", false,
                "default_branch", "main"
        );
        Map<String, Object> created = restClient.post()
                .uri("/projects")
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        Integer id = (Integer) created.get("id");
        log.info("Created GitLab project '{}' with id={}", name, id);
        return id;
    }

    // ── Files ─────────────────────────────────────────────────

    /**
     * A single file action within an atomic commit.
     * Action must be one of GitLab's supported values: "create", "update", "delete", "move", "chmod".
     */
    public record FileAction(String action, String filePath, String content) {}

    /**
     * Commit multiple file changes in a single atomic commit via the Commits API.
     * This produces exactly one pipeline trigger regardless of how many files change,
     * avoiding the auto-cancel race that occurs when files are pushed one-at-a-time.
     *
     * @param projectId     the GitLab project id
     * @param commitMessage commit message
     * @param actions       list of file actions to include in the commit
     * @return the SHA of the created commit
     */
    public String commitFiles(Integer projectId, String commitMessage, List<FileAction> actions) {
        log.debug("Committing {} file action(s) to project id={}", actions.size(), projectId);

        List<Map<String, String>> actionBodies = actions.stream()
                .map(a -> Map.of(
                        "action", a.action(),
                        "file_path", a.filePath(),
                        "content", a.content()))
                .toList();

        Map<String, Object> body = Map.of(
                "branch", "main",
                "commit_message", commitMessage,
                "actions", actionBodies
        );
        Map<String, Object> response = restClient.post()
                .uri("/projects/{projectId}/repository/commits", projectId)
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        String sha = response != null ? (String) response.get("id") : null;
        if (sha == null) {
            throw new IllegalStateException("GitLab commit response missing 'id' for project " + projectId);
        }
        log.debug("Created commit {} in project id={}", sha, projectId);
        return sha;
    }

    // ── Webhooks ──────────────────────────────────────────────

    /**
     * Register a pipeline webhook on a GitLab project.
     * GitLab will POST to the given URL when a pipeline finishes.
     * The configured webhook secret is used as the token header for verification.
     *
     * @param projectId  the GitLab project id
     * @param webhookUrl the Spring Boot endpoint that will receive pipeline events
     */
    public void registerWebhook(Integer projectId, String webhookUrl) {
        log.info("Registering webhook on project id={} → {}", projectId, webhookUrl);

        Map<String, Object> body = Map.of(
                "url", webhookUrl,
                "token", properties.webhookSecret(),
                "pipeline_events", true,
                "push_events", false
        );
        restClient.post()
                .uri("/projects/{projectId}/hooks", projectId)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    // ── Pipelines ─────────────────────────────────────────────

    /**
     * Fetch the pipeline created for a specific commit SHA.
     * Retries up to 15 times with 2-second pauses — GitLab may take a while
     * to queue the pipeline after the commit, especially on cold instances.
     * Filtering by SHA guarantees we return the pipeline for our commit and
     * not a stale one from a prior attempt.
     *
     * @param projectId the GitLab project id
     * @param sha       the commit SHA to match
     * @return the pipeline DTO for the given SHA
     * @throws IllegalStateException if no pipeline appears after all attempts
     */
    public GitLabPipelineDto getPipelineForSha(Integer projectId, String sha) {
        int maxAttempts = 15;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            log.debug("Fetching pipeline for project id={} sha={} (attempt {}/{})",
                    projectId, sha, attempt, maxAttempts);
            List<GitLabPipelineDto> pipelines = restClient.get()
                    .uri("/projects/{projectId}/pipelines?sha={sha}&per_page=1&order_by=id&sort=desc",
                            projectId, sha)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (pipelines != null && !pipelines.isEmpty()) {
                log.debug("Pipeline appeared for project id={} sha={} on attempt {}",
                        projectId, sha, attempt);
                return pipelines.getFirst();
            }
            if (attempt < maxAttempts) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(
                            "Interrupted while waiting for pipeline on project " + projectId);
                }
            }
        }
        throw new IllegalStateException(
                "No pipeline found for project " + projectId + " sha " + sha + " after "
                        + maxAttempts + " attempts (30s). Check .gitlab-ci.yml validity.");
    }

    /**
     * Fetch pipeline details including current status from GitLab.
     *
     * @param projectId  the GitLab project id
     * @param pipelineId the GitLab pipeline id
     * @return pipeline details DTO
     */
    public GitLabPipelineDto getPipeline(Integer projectId, Integer pipelineId) {
        log.debug("Fetching pipeline id={} for project id={}", pipelineId, projectId);
        return restClient.get()
                .uri("/projects/{projectId}/pipelines/{pipelineId}", projectId, pipelineId)
                .retrieve()
                .body(GitLabPipelineDto.class);
    }

    /**
     * Fetch the raw log output for a specific pipeline job.
     *
     * @param projectId the GitLab project id
     * @param jobId     the GitLab job id
     * @return raw log text
     */
    public String getJobLog(Integer projectId, Integer jobId) {
        log.debug("Fetching log for job id={} in project id={}", jobId, projectId);
        return restClient.get()
                .uri("/projects/{projectId}/jobs/{jobId}/trace", projectId, jobId)
                .retrieve()
                .body(String.class);
    }

    /**
     * Fetch all jobs belonging to a pipeline.
     *
     * @param projectId  the GitLab project id
     * @param pipelineId the GitLab pipeline id
     * @return list of job DTOs
     */
    public List<GitLabJobDto> getPipelineJobs(Integer projectId, Integer pipelineId) {
        log.debug("Fetching jobs for pipeline id={} in project id={}", pipelineId, projectId);
        return restClient.get()
                .uri("/projects/{projectId}/pipelines/{pipelineId}/jobs", projectId, pipelineId)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }
}
