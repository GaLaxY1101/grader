-- ============================================================
-- V13: Refactor submissions → one per student-assignment pair,
--      introduce attempts table for each code upload.
-- ============================================================

-- 1. Create the attempts table
CREATE TABLE attempts (
    id                  BIGSERIAL                   PRIMARY KEY,
    submission_id       BIGINT                      NOT NULL,
    attempt_number      INTEGER                     NOT NULL,
    status              VARCHAR(20)                 NOT NULL DEFAULT 'PENDING',
    code_content        TEXT,
    score               INTEGER,
    gitlab_pipeline_id  BIGINT,
    pipeline_output     TEXT,
    submitted_at        TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_attempts_submissions
        FOREIGN KEY (submission_id) REFERENCES submissions(id)
);

CREATE INDEX idx_attempts_submission_id ON attempts(submission_id);
CREATE INDEX idx_attempts_gitlab_pipeline ON attempts(gitlab_pipeline_id);
CREATE UNIQUE INDEX uq_attempts_submission_number ON attempts(submission_id, attempt_number);

-- 2. Truncate submissions (dev-only DB, no production data)
TRUNCATE TABLE submissions CASCADE;

-- 3. Drop attempt-specific columns from submissions
ALTER TABLE submissions DROP COLUMN code_content;
ALTER TABLE submissions DROP COLUMN gitlab_pipeline_id;
ALTER TABLE submissions DROP COLUMN pipeline_output;
ALTER TABLE submissions DROP COLUMN submitted_at;

-- 4. Add aggregate columns to submissions
ALTER TABLE submissions ADD COLUMN best_score INTEGER;
ALTER TABLE submissions ADD COLUMN latest_attempt_id BIGINT;
ALTER TABLE submissions ADD CONSTRAINT fk_submissions_latest_attempt
    FOREIGN KEY (latest_attempt_id) REFERENCES attempts(id);

-- 5. Enforce one submission per student-assignment pair
ALTER TABLE submissions ADD CONSTRAINT uq_submissions_assignment_student
    UNIQUE (assignment_id, student_id);

-- 6. Drop redundant indexes (unique constraint covers the composite, pipeline index moved to attempts)
DROP INDEX IF EXISTS idx_submissions_assignment_student;
DROP INDEX IF EXISTS idx_submissions_gitlab_project_pipeline;
