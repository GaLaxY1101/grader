-- ============================================================
-- V24: Submission file-workflow state + student-uploaded
--      submission attachments. Google Classroom style:
--      DRAFT -> SUBMITTED -> RETURNED -> SUBMITTED -> GRADED.
--      file_state is nullable so CODE-only submissions stay
--      unaffected.
-- ============================================================

ALTER TABLE submissions
    ADD COLUMN file_state VARCHAR(20)
        CHECK (file_state IN ('DRAFT', 'SUBMITTED', 'RETURNED', 'GRADED')),
    ADD COLUMN return_comment TEXT,
    ADD COLUMN returned_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN submitted_at TIMESTAMP WITH TIME ZONE;

CREATE TABLE submission_attachments (
    id              BIGSERIAL                   PRIMARY KEY,
    submission_id   BIGINT                      NOT NULL,
    filename        VARCHAR(512)                NOT NULL,
    content_type    VARCHAR(255),
    size_bytes      BIGINT                      NOT NULL,
    storage_key     VARCHAR(1024)               NOT NULL UNIQUE,
    uploaded_at     TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_submission_attachments_submissions
        FOREIGN KEY (submission_id) REFERENCES submissions(id) ON DELETE CASCADE
);

CREATE INDEX idx_submission_attachments_submission
    ON submission_attachments(submission_id);
