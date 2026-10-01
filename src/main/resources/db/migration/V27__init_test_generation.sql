-- ============================================================
-- V27: AI test generation jobs and their self-repair iterations.
--      Iterations store every prompt, LLM output and sandbox
--      metric; they are the raw data for the evaluation.
-- ============================================================

CREATE TABLE test_generation_jobs (
    id                  BIGSERIAL                   PRIMARY KEY,
    assignment_id       BIGINT,
    status              VARCHAR(20)                 NOT NULL
        CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    language            VARCHAR(50)                 NOT NULL,
    model               VARCHAR(100)                NOT NULL,
    config              JSONB                       NOT NULL,
    task_description    TEXT,
    function_signature  TEXT,
    reference_solution  TEXT                        NOT NULL,
    final_test_content  TEXT,
    error_message       TEXT,
    created_by          VARCHAR(255),
    created_at          TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW(),
    finished_at         TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_test_generation_jobs_assignments
        FOREIGN KEY (assignment_id) REFERENCES assignments(id) ON DELETE SET NULL
);

CREATE INDEX idx_test_generation_jobs_assignment ON test_generation_jobs(assignment_id);
CREATE INDEX idx_test_generation_jobs_created_by_status ON test_generation_jobs(created_by, status);

CREATE TABLE test_generation_iterations (
    id                  BIGSERIAL                   PRIMARY KEY,
    job_id              BIGINT                      NOT NULL,
    iteration_no        INT                         NOT NULL,
    prompt_type         VARCHAR(30)                 NOT NULL
        CHECK (prompt_type IN ('GENERATE', 'REPAIR_COMPILE', 'REPAIR_FAILING', 'KILL_MUTANT')),
    prompt              TEXT,
    llm_output          TEXT,
    test_content        TEXT,
    compile_ok          BOOLEAN,
    ref_passed          INT,
    ref_total           INT,
    mutants_killed      INT,
    mutants_total       INT,
    test_count          INT,
    accepted            BOOLEAN,
    feedback            TEXT,
    duration_ms         BIGINT,
    prompt_tokens       INT,
    completion_tokens   INT,
    created_at          TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_test_generation_iterations_test_generation_jobs
        FOREIGN KEY (job_id) REFERENCES test_generation_jobs(id) ON DELETE CASCADE,
    CONSTRAINT uq_test_generation_iterations_job_iteration UNIQUE (job_id, iteration_no)
);
