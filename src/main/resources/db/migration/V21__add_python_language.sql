ALTER TABLE programming_tasks
    DROP CONSTRAINT IF EXISTS chk_programming_tasks_language;

ALTER TABLE programming_tasks
    ADD CONSTRAINT chk_programming_tasks_language
        CHECK (language IN ('C', 'CPP', 'PYTHON'));

ALTER TABLE template_programming_tasks
    DROP CONSTRAINT IF EXISTS chk_template_programming_tasks_language;

ALTER TABLE template_programming_tasks
    ADD CONSTRAINT chk_template_programming_tasks_language
        CHECK (language IN ('C', 'CPP', 'PYTHON'));
