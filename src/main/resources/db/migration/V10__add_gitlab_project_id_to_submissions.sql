ALTER TABLE submissions
    ADD COLUMN gitlab_project_id BIGINT;

CREATE INDEX idx_submissions_gitlab_project_id
    ON submissions (gitlab_project_id);

CREATE INDEX idx_submissions_gitlab_project_pipeline
    ON submissions (gitlab_project_id, gitlab_pipeline_id);
