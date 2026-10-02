-- ============================================================
-- V32: How much of the per-test feedback students see.
--      FULL       - test names, expected and actual values
--      NAMES_ONLY - pass/fail per test, no values (hidden tests)
--      SUMMARY    - only the number of passed tests
--      Teachers and admins always see everything.
-- ============================================================

ALTER TABLE programming_tasks
    ADD COLUMN feedback_level VARCHAR(20) NOT NULL DEFAULT 'FULL';

ALTER TABLE template_programming_tasks
    ADD COLUMN feedback_level VARCHAR(20) NOT NULL DEFAULT 'FULL';
