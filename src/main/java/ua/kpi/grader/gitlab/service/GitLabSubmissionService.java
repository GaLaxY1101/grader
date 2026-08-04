package ua.kpi.grader.gitlab.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ua.kpi.grader.course.entity.ProgrammingTask;
import ua.kpi.grader.course.repository.ProgrammingTaskRepository;
import ua.kpi.grader.gitlab.client.GitLabApiClient;
import ua.kpi.grader.gitlab.client.dto.GitLabPipelineDto;
import ua.kpi.grader.gitlab.config.GitLabProperties;
import ua.kpi.grader.submission.entity.Attempt;
import ua.kpi.grader.submission.entity.Submission;
import ua.kpi.grader.submission.entity.SubmissionStatus;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitLabSubmissionService {

    private final GitLabApiClient gitLabApiClient;
    private final CiConfigService ciConfigService;
    private final ProgrammingTaskRepository programmingTaskRepository;
    private final GitLabProperties properties;

    /**
     * Orchestrates the full GitLab pipeline trigger for an attempt.
     * On the first attempt, creates a GitLab project, pushes the student's code
     * and .gitlab-ci.yml, and registers the webhook.
     * On subsequent attempts, updates the existing files to trigger a new pipeline.
     * On any failure, marks the attempt as ERROR and does not throw.
     *
     * @param submission the parent submission (one per student-assignment)
     * @param attempt    the new attempt to trigger a pipeline for
     */
    public void triggerPipeline(Submission submission, Attempt attempt) {
        Long assignmentId = submission.getAssignment().getId();
        Long studentId = submission.getStudent().getId();

        log.info("Triggering GitLab pipeline for attempt id={} (submission={}, assignment={}, student={})",
                attempt.getId(), submission.getId(), assignmentId, studentId);
        try {
            ProgrammingTask task = programmingTaskRepository
                    .findByAssignmentId(assignmentId)
                    .orElseThrow(() -> new IllegalStateException(
                            "No programming task found for assignment " + assignmentId));

            String ciYaml = ciConfigService.generateCiConfig(task.getCiConfigTemplate());
            String solutionFileName = task.getLanguage().getSolutionFileName();

            boolean isFirstAttempt = submission.getGitlabProjectId() == null;

            if (isFirstAttempt) {
                Integer groupId = gitLabApiClient.getOrCreateGroup();
                Integer projectId = gitLabApiClient.createProject(assignmentId, studentId, groupId);
                submission.assignGitlabProject(projectId.longValue());

                gitLabApiClient.pushFile(projectId, solutionFileName,
                        attempt.getCodeContent(), "Add student solution");

                gitLabApiClient.pushFile(projectId, "test.cpp",
                        task.getTestFileContent(), "Add teacher test file");

                gitLabApiClient.pushFile(projectId, ".gitlab-ci.yml", ciYaml, "Add CI config");

                String webhookUrl = properties.webhookBaseUrl() + "/api/webhooks/gitlab";
                gitLabApiClient.registerWebhook(projectId, webhookUrl);
            } else {
                Integer projectId = submission.getGitlabProjectId().intValue();

                gitLabApiClient.updateFile(projectId, solutionFileName,
                        attempt.getCodeContent(),
                        "Update student solution (attempt %d)".formatted(attempt.getAttemptNumber()));

                gitLabApiClient.updateFile(projectId, "test.cpp",
                        task.getTestFileContent(),
                        "Update teacher test file (attempt %d)".formatted(attempt.getAttemptNumber()));

                gitLabApiClient.updateFile(projectId, ".gitlab-ci.yml", ciYaml,
                        "Update CI config (attempt %d)".formatted(attempt.getAttemptNumber()));
            }

            Integer projectId = submission.getGitlabProjectId().intValue();
            GitLabPipelineDto pipeline = gitLabApiClient.getLatestPipeline(projectId);

            attempt.startPipeline(pipeline.id().longValue());
            log.info("Pipeline triggered: project={}, pipeline={}, attempt={}",
                    projectId, pipeline.id(), attempt.getAttemptNumber());

        } catch (IllegalStateException e) {
            log.error("Failed to trigger GitLab pipeline for attempt id={}: {}",
                    attempt.getId(), e.getMessage(), e);
            attempt.applyResult(SubmissionStatus.ERROR, null,
                    "Pipeline trigger failed: " + e.getMessage());
            submission.updateFromAttempt(attempt);
        } catch (org.springframework.web.client.RestClientException e) {
            log.error("GitLab API error for attempt id={}: {}",
                    attempt.getId(), e.getMessage(), e);
            attempt.applyResult(SubmissionStatus.ERROR, null,
                    "GitLab API error: " + e.getMessage());
            submission.updateFromAttempt(attempt);
        }
    }
}
