CREATE TABLE course_templates
(
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    owner_id    BIGINT       NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT fk_course_templates_teachers FOREIGN KEY (owner_id) REFERENCES teachers (id)
);

CREATE INDEX idx_course_templates_owner_id ON course_templates (owner_id);

CREATE TABLE template_assignments
(
    id          BIGSERIAL PRIMARY KEY,
    template_id BIGINT       NOT NULL,
    title       VARCHAR(255) NOT NULL,
    description TEXT,
    max_score   INTEGER      NOT NULL DEFAULT 100,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT fk_template_assignments_course_templates
        FOREIGN KEY (template_id) REFERENCES course_templates (id) ON DELETE CASCADE
);

CREATE INDEX idx_template_assignments_template_id ON template_assignments (template_id);

CREATE TABLE template_programming_tasks
(
    id                     BIGSERIAL   PRIMARY KEY,
    template_assignment_id BIGINT      NOT NULL,
    language               VARCHAR(50) NOT NULL,
    ci_config_template     TEXT,
    test_mode              VARCHAR(20) NOT NULL DEFAULT 'IO',
    function_signature     TEXT,
    test_file_content      TEXT,
    CONSTRAINT fk_template_programming_tasks_template_assignments
        FOREIGN KEY (template_assignment_id) REFERENCES template_assignments (id) ON DELETE CASCADE,
    CONSTRAINT uq_template_programming_tasks_assignment_id UNIQUE (template_assignment_id),
    CONSTRAINT chk_template_programming_tasks_language CHECK (language IN ('C', 'CPP')),
    CONSTRAINT chk_template_programming_tasks_test_mode CHECK (test_mode IN ('IO', 'UNIT_TEST'))
);

CREATE TABLE template_test_cases
(
    id                           BIGSERIAL    PRIMARY KEY,
    template_programming_task_id BIGINT       NOT NULL,
    name                         VARCHAR(255) NOT NULL,
    test_type                    VARCHAR(20)  NOT NULL DEFAULT 'IO',
    input                        TEXT,
    expected_output              TEXT,
    CONSTRAINT fk_template_test_cases_template_programming_tasks
        FOREIGN KEY (template_programming_task_id) REFERENCES template_programming_tasks (id) ON DELETE CASCADE
);

CREATE INDEX idx_template_test_cases_task_id ON template_test_cases (template_programming_task_id);

CREATE TABLE template_shares
(
    id                     BIGSERIAL PRIMARY KEY,
    template_id            BIGINT NOT NULL,
    shared_with_teacher_id BIGINT NOT NULL,
    shared_by_teacher_id   BIGINT NOT NULL,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT fk_template_shares_course_templates
        FOREIGN KEY (template_id) REFERENCES course_templates (id) ON DELETE CASCADE,
    CONSTRAINT fk_template_shares_teachers_shared_with
        FOREIGN KEY (shared_with_teacher_id) REFERENCES teachers (id),
    CONSTRAINT fk_template_shares_teachers_shared_by
        FOREIGN KEY (shared_by_teacher_id) REFERENCES teachers (id),
    CONSTRAINT uq_template_shares_template_teacher UNIQUE (template_id, shared_with_teacher_id)
);

CREATE INDEX idx_template_shares_template_id            ON template_shares (template_id);
CREATE INDEX idx_template_shares_shared_with_teacher_id ON template_shares (shared_with_teacher_id);
