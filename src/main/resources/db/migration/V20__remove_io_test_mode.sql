-- Removes IO test mode support: only UNIT_TEST remains.
-- Drops per-test-case tables and backfills any lingering IO rows.

DROP TABLE IF EXISTS test_cases;
DROP TABLE IF EXISTS template_test_cases;

UPDATE programming_tasks          SET test_mode = 'UNIT_TEST' WHERE test_mode <> 'UNIT_TEST';
UPDATE template_programming_tasks SET test_mode = 'UNIT_TEST' WHERE test_mode <> 'UNIT_TEST';

ALTER TABLE programming_tasks          DROP CONSTRAINT IF EXISTS chk_programming_tasks_test_mode;
ALTER TABLE template_programming_tasks DROP CONSTRAINT IF EXISTS chk_template_programming_tasks_test_mode;

ALTER TABLE programming_tasks          ALTER COLUMN test_mode SET DEFAULT 'UNIT_TEST';
ALTER TABLE template_programming_tasks ALTER COLUMN test_mode SET DEFAULT 'UNIT_TEST';

ALTER TABLE programming_tasks
    ADD CONSTRAINT chk_programming_tasks_test_mode CHECK (test_mode IN ('UNIT_TEST'));
ALTER TABLE template_programming_tasks
    ADD CONSTRAINT chk_template_programming_tasks_test_mode CHECK (test_mode IN ('UNIT_TEST'));
