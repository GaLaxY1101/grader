package ua.kpi.grader.gitlab.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ua.kpi.grader.course.entity.ProgrammingTask;
import ua.kpi.grader.course.repository.ProgrammingTaskRepository;
import ua.kpi.grader.gitlab.client.GitLabApiClient;
import ua.kpi.grader.gitlab.client.GitLabApiClient.FileAction;
import ua.kpi.grader.gitlab.client.dto.GitLabPipelineDto;
import ua.kpi.grader.gitlab.config.GitLabProperties;
import ua.kpi.grader.submission.entity.Attempt;
import ua.kpi.grader.submission.entity.Submission;
import ua.kpi.grader.submission.entity.SubmissionStatus;

import java.util.List;

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
            String commitSha;

            if (isFirstAttempt) {
                Integer groupId = gitLabApiClient.getOrCreateGroup();
                Integer newProjectId = gitLabApiClient.createProject(assignmentId, studentId, groupId);
                submission.assignGitlabProject(newProjectId.longValue());

                commitSha = gitLabApiClient.commitFiles(newProjectId,
                        "Initial submission (attempt %d)".formatted(attempt.getAttemptNumber()),
                        List.of(
                                new FileAction("create", solutionFileName, attempt.getCodeContent()),
                                new FileAction("create", "test.cpp", task.getTestFileContent()),
                                new FileAction("create", ".gitlab-ci.yml", ciYaml)
                        ));

                String webhookUrl = properties.webhookBaseUrl() + "/api/webhooks/gitlab";
                gitLabApiClient.registerWebhook(newProjectId, webhookUrl);
            } else {
                Integer existingProjectId = submission.getGitlabProjectId().intValue();

                commitSha = gitLabApiClient.commitFiles(existingProjectId,
                        "Attempt %d".formatted(attempt.getAttemptNumber()),
                        List.of(
                                new FileAction("update", solutionFileName, attempt.getCodeContent()),
                                new FileAction("update", "test.cpp", task.getTestFileContent()),
                                new FileAction("update", ".gitlab-ci.yml", ciYaml)
                        ));
            }

            Integer projectId = submission.getGitlabProjectId().intValue();
            GitLabPipelineDto pipeline = gitLabApiClient.getPipelineForSha(projectId, commitSha);

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
