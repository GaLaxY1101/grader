-- ============================================================
-- V31: Structured per-test results of an attempt, parsed from
--      the grader harness events in the CI job log.
-- ============================================================

CREATE TABLE attempt_test_results (
    id           BIGSERIAL                   PRIMARY KEY,
    attempt_id   BIGINT                      NOT NULL,
    position     INT                         NOT NULL,
    name         VARCHAR(255)                NOT NULL,
    status       VARCHAR(20)                 NOT NULL,
    expected     TEXT,
    actual       TEXT,
    message      TEXT,
    duration_ms  BIGINT,
    created_at   TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_attempt_test_results_attempts
        FOREIGN KEY (attempt_id) REFERENCES attempts (id) ON DELETE CASCADE
);

CREATE INDEX idx_attempt_test_results_attempt ON attempt_test_results (attempt_id, position);

ALTER TABLE attempts ADD COLUMN tests_passed       INT;
ALTER TABLE attempts ADD COLUMN tests_total        INT;
ALTER TABLE attempts ADD COLUMN test_run_status    VARCHAR(20);
ALTER TABLE attempts ADD COLUMN compile_output     TEXT;
