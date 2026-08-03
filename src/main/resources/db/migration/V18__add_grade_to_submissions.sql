-- ============================================================
-- V18: Add teacher-editable grade column to submissions.
--      Distinct from best_score so teacher overrides survive
--      future auto-test runs.
-- ============================================================

ALTER TABLE submissions ADD COLUMN grade INTEGER;
