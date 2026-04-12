-- GitLab credentials moved out of profile tables.
-- They will live in a dedicated gitlab/ module (Session 6).

ALTER TABLE teachers
    DROP COLUMN IF EXISTS gitlab_id,
    DROP COLUMN IF EXISTS gitlab_token;

ALTER TABLE students
    DROP COLUMN IF EXISTS gitlab_id,
    DROP COLUMN IF EXISTS gitlab_token;
