-- ============================================================
-- V28: Evaluation metrics per self-repair iteration.
--      heldout_* = kills among held-out mutants (set B), which
--      are never shown to the LLM; coverage_pct = line coverage
--      of the reference solution by the iteration's test file.
-- ============================================================

ALTER TABLE test_generation_iterations
    ADD COLUMN heldout_killed INT,
    ADD COLUMN heldout_total  INT,
    ADD COLUMN coverage_pct   DOUBLE PRECISION;
