ALTER TABLE programming_tasks
    ADD COLUMN test_mode VARCHAR(20) NOT NULL DEFAULT 'IO';

ALTER TABLE programming_tasks
    ADD COLUMN function_signature TEXT;

ALTER TABLE programming_tasks
    ADD COLUMN test_file_content TEXT;

ALTER TABLE programming_tasks
    ADD CONSTRAINT chk_programming_tasks_test_mode
        CHECK (test_mode IN ('IO', 'UNIT_TEST'));
