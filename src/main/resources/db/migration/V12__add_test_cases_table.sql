CREATE TABLE test_cases
(
    id                  BIGSERIAL    PRIMARY KEY,
    programming_task_id BIGINT       NOT NULL,
    name                VARCHAR(255) NOT NULL,
    test_type           VARCHAR(20)  NOT NULL DEFAULT 'IO',
    input               TEXT,
    expected_output     TEXT,
    CONSTRAINT fk_test_cases_programming_tasks
        FOREIGN KEY (programming_task_id) REFERENCES programming_tasks (id)
);

CREATE INDEX idx_test_cases_programming_task_id ON test_cases (programming_task_id);
