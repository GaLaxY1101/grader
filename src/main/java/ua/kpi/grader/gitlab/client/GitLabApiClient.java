package ua.kpi.grader.gitlab.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;
import ua.kpi.grader.gitlab.client.dto.GitLabJobDto;
import ua.kpi.grader.gitlab.client.dto.GitLabPipelineDto;
import ua.kpi.grader.gitlab.config.GitLabProperties;

import java.nio.charset.StandardCharsets;
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
     * Create a new private GitLab project for a student/assignment pair.
     * Project name and path follow the pattern: assignment-{assignmentId}-student-{studentId}.
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
                "initialize_with_readme", false
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
     * Push a single file to a GitLab project repository via the Files API.
     * The file path is URL-encoded to handle slashes (e.g. src/main.c → src%2Fmain.c).
     *
     * @param projectId     the GitLab project id
     * @param filePath      relative path inside the repo (e.g. "solution.c")
     * @param content       raw file content
     * @param commitMessage commit message
     */
    public void pushFile(Integer projectId, String filePath, String content, String commitMessage) {
        String encodedPath = UriUtils.encodePathSegment(filePath, StandardCharsets.UTF_8);
        log.debug("Pushing file '{}' to project id={}", filePath, projectId);

        Map<String, Object> body = Map.of(
                "branch", "main",
                "content", content,
                "commit_message", commitMessage
        );
        restClient.post()
                .uri("/projects/{projectId}/repository/files/{filePath}", projectId, encodedPath)
                .body(body)
                .retrieve()
                .toBodilessEntity();
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
