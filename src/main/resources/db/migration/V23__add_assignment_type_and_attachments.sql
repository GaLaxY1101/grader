-- ============================================================
-- V23: Assignment type enum + teacher-uploaded assignment
--      attachments. Enables FILE and CODE_FILE assignment kinds
--      alongside the existing CODE flow.
-- ============================================================

-- 1. Assignment type (CODE default, backfills existing rows)
ALTER TABLE assignments
    ADD COLUMN type VARCHAR(20) NOT NULL DEFAULT 'CODE'
        CHECK (type IN ('CODE', 'FILE', 'CODE_FILE'));

-- 2. Teacher-uploaded static attachments (specs, starter files)
CREATE TABLE assignment_attachments (
    id              BIGSERIAL                   PRIMARY KEY,
    assignment_id   BIGINT                      NOT NULL,
    filename        VARCHAR(512)                NOT NULL,
    content_type    VARCHAR(255),
    size_bytes      BIGINT                      NOT NULL,
    storage_key     VARCHAR(1024)               NOT NULL UNIQUE,
    uploaded_by     BIGINT                      NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_assignment_attachments_assignments
        FOREIGN KEY (assignment_id) REFERENCES assignments(id) ON DELETE CASCADE,
    CONSTRAINT fk_assignment_attachments_teachers
        FOREIGN KEY (uploaded_by) REFERENCES teachers(id)
);

CREATE INDEX idx_assignment_attachments_assignment
    ON assignment_attachments(assignment_id);
