-- ============================================================
-- V29: Sampling temperature actually used for each iteration's LLM
--      call (raised after a repair returned an unchanged file).
-- ============================================================

ALTER TABLE test_generation_iterations
    ADD COLUMN temperature DOUBLE PRECISION;
