ALTER TABLE programming_tasks
    ADD CONSTRAINT chk_programming_tasks_language
        CHECK (language IN ('C', 'CPP'));
