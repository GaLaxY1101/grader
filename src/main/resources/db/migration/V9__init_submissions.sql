CREATE TABLE submissions (
    id                  BIGSERIAL                   PRIMARY KEY,
    assignment_id       BIGINT                      NOT NULL,
    student_id          BIGINT                      NOT NULL,
    status              VARCHAR(20)                 NOT NULL DEFAULT 'PENDING',
    code_content        TEXT,
    score               INTEGER,
    gitlab_pipeline_id  BIGINT,
    pipeline_output     TEXT,
    submitted_at        TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_submissions_assignments FOREIGN KEY (assignment_id) REFERENCES assignments(id),
    CONSTRAINT fk_submissions_students    FOREIGN KEY (student_id)    REFERENCES students(id)
);

CREATE INDEX idx_submissions_assignment_id        ON submissions(assignment_id);
CREATE INDEX idx_submissions_student_id           ON submissions(student_id);
CREATE INDEX idx_submissions_assignment_student   ON submissions(assignment_id, student_id);
CREATE INDEX idx_submissions_status               ON submissions(status);
