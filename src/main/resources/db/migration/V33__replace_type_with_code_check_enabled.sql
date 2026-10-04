-- ============================================================
-- V33: Replace the three-value assignment type enum with a
--      single boolean code_check_enabled flag on both
--      assignments and template_assignments.
--      Existing CODE and CODE_FILE rows → true.
--      Existing FILE rows → false.
--      All assignments now implicitly support student file uploads.
-- ============================================================

-- assignments
ALTER TABLE assignments ADD COLUMN code_check_enabled BOOLEAN;
UPDATE assignments SET code_check_enabled = (type IN ('CODE', 'CODE_FILE'));
ALTER TABLE assignments ALTER COLUMN code_check_enabled SET NOT NULL;
ALTER TABLE assignments ALTER COLUMN code_check_enabled SET DEFAULT FALSE;
ALTER TABLE assignments DROP COLUMN type;

-- template_assignments
ALTER TABLE template_assignments ADD COLUMN code_check_enabled BOOLEAN;
UPDATE template_assignments SET code_check_enabled = (type IN ('CODE', 'CODE_FILE'));
ALTER TABLE template_assignments ALTER COLUMN code_check_enabled SET NOT NULL;
ALTER TABLE template_assignments ALTER COLUMN code_check_enabled SET DEFAULT FALSE;
ALTER TABLE template_assignments DROP COLUMN type;
