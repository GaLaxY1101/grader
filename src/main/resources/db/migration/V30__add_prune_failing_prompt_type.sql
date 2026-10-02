-- ============================================================
-- V30: PRUNE_FAILING iterations: tests that still fail on the
--      reference solution after the repair loop are removed
--      deterministically (no LLM call).
-- ============================================================

ALTER TABLE test_generation_iterations
    DROP CONSTRAINT test_generation_iterations_prompt_type_check;

ALTER TABLE test_generation_iterations
    ADD CONSTRAINT test_generation_iterations_prompt_type_check
        CHECK (prompt_type IN ('GENERATE', 'REPAIR_COMPILE', 'REPAIR_FAILING', 'KILL_MUTANT', 'PRUNE_FAILING'));
