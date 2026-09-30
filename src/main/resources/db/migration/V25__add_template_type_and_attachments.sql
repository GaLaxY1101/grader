-- ============================================================
-- V25: Template assignment type + teacher-uploaded template
--      attachments. Mirrors V23 for course assignments so that
--      templates can carry FILE/CODE_FILE assignments and the
--      instantiate flow can clone attachments into a new course.
-- ============================================================

ALTER TABLE template_assignments
    ADD COLUMN type VARCHAR(20) NOT NULL DEFAULT 'CODE'
        CHECK (type IN ('CODE', 'FILE', 'CODE_FILE'));

CREATE TABLE template_assignment_attachments (
    id                     BIGSERIAL                   PRIMARY KEY,
    template_assignment_id BIGINT                      NOT NULL,
    filename               VARCHAR(512)                NOT NULL,
    content_type           VARCHAR(255),
    size_bytes             BIGINT                      NOT NULL,
    storage_key            VARCHAR(1024)               NOT NULL UNIQUE,
    uploaded_by            BIGINT                      NOT NULL,
    created_at             TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_template_assignment_attachments_template_assignments
        FOREIGN KEY (template_assignment_id) REFERENCES template_assignments(id) ON DELETE CASCADE,
    CONSTRAINT fk_template_assignment_attachments_teachers
        FOREIGN KEY (uploaded_by) REFERENCES teachers(id)
);

CREATE INDEX idx_template_assignment_attachments_template_assignment
    ON template_assignment_attachments(template_assignment_id);
