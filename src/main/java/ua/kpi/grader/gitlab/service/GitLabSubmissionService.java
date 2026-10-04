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
import ua.kpi.grader.submission.feedback.GraderHarness;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitLabSubmissionService {

    private final GitLabApiClient gitLabApiClient;
    private final CiConfigService ciConfigService;
    private final ProgrammingTaskRepository programmingTaskRepository;
    private final GitLabProperties properties;
    private final GraderHarness graderHarness;

    /**
     * Orchestrates the full GitLab pipeline trigger for an attempt.
     * On the first attempt, creates a GitLab project, pushes the student's code, the test file,
     * the grader harness and .gitlab-ci.yml, and registers the webhook.
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

            String ciYaml = ciConfigService.generateCiConfig(task.getCiConfigTemplate(), task.getLanguage());
            String solutionFileName = task.getLanguage().getSolutionFileName();
            String testFileName = task.getLanguage().getTestFileName();
            String harnessFileName = graderHarness.fileName(task.getLanguage());
            String harnessContent = graderHarness.content(task.getLanguage());

            boolean isFirstAttempt = submission.getGitlabProjectId() == null;
            String commitSha;

            if (isFirstAttempt) {
                Integer groupId = gitLabApiClient.getOrCreateGroup();
                String projectPath = "assignment-%d-student-%d".formatted(assignmentId, studentId);

                // Reuse an orphan project left over from a prior run (e.g. local DB wiped but GitLab volume kept)
                // instead of failing with 400 "has already been taken".
                var existing = gitLabApiClient.findProjectByPath(properties.groupName(), projectPath);
                Integer projectId;
                boolean freshlyCreated;
                if (existing.isPresent()) {
                    projectId = existing.get();
                    freshlyCreated = false;
                    log.info("Reusing existing GitLab project id={} for submission id={}",
                            projectId, submission.getId());
                } else {
                    projectId = gitLabApiClient.createProject(assignmentId, studentId, groupId);
                    freshlyCreated = true;
                }
                submission.assignGitlabProject(projectId.longValue());

                commitSha = gitLabApiClient.commitFiles(projectId,
                        "Initial submission (attempt %d)".formatted(attempt.getAttemptNumber()),
                        List.of(
                                fileAction(projectId, freshlyCreated, solutionFileName, attempt.getCodeContent()),
                                fileAction(projectId, freshlyCreated, testFileName, task.getTestFileContent()),
                                fileAction(projectId, freshlyCreated, harnessFileName, harnessContent),
                                fileAction(projectId, freshlyCreated, ".gitlab-ci.yml", ciYaml)
                        ));
            } else {
                Integer existingProjectId = submission.getGitlabProjectId().intValue();
                // Projects created before the harness existed do not have the file yet.
                String harnessAction = gitLabApiClient.fileExists(existingProjectId, harnessFileName)
                        ? "update" : "create";

                commitSha = gitLabApiClient.commitFiles(existingProjectId,
                        "Attempt %d".formatted(attempt.getAttemptNumber()),
                        List.of(
                                new FileAction("update", solutionFileName, attempt.getCodeContent()),
                                new FileAction("update", testFileName, task.getTestFileContent()),
                                new FileAction(harnessAction, harnessFileName, harnessContent),
                                new FileAction("update", ".gitlab-ci.yml", ciYaml)
                        ));
            }

            Integer projectId = submission.getGitlabProjectId().intValue();
            // Ensure the webhook exists on every attempt. Covers reused orphan projects and
            // submissions whose first attempt pre-dates this check.
            String webhookUrl = properties.webhookBaseUrl() + "/api/webhooks/gitlab";
            if (!gitLabApiClient.hasWebhook(projectId, webhookUrl)) {
                gitLabApiClient.registerWebhook(projectId, webhookUrl);
            }

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

    private FileAction fileAction(Integer projectId, boolean freshlyCreated, String path, String content) {
        String action = freshlyCreated || !gitLabApiClient.fileExists(projectId, path) ? "create" : "update";
        return new FileAction(action, path, content);
    }
}
