-- Demo seed data for development/testing
-- Passwords are BCrypt-hashed at cost factor 10
-- admin@grader.ua    → Admin123!
-- teacher@grader.ua  → Teacher123!
-- student@grader.ua  → Student123!

INSERT INTO users (email, password_hash, first_name, last_name, role, is_active)
VALUES ('admin@grader.ua',
        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LPVImvdHV4i',
        'System', 'Admin', 'ADMIN', TRUE);

INSERT INTO admins (user_id)
VALUES (currval('users_id_seq'));

INSERT INTO users (email, password_hash, first_name, last_name, role, is_active)
VALUES ('teacher@grader.ua',
        '$2a$10$c.eakIfMDIy/Q8HS1nrNO.5HxkUBVOkPOVz0bQCJzL6Z8JxJ8Z8Gy',
        'Olena', 'Kovalenko', 'TEACHER', TRUE);

INSERT INTO teachers (user_id, department, academic_degree)
VALUES (currval('users_id_seq'), 'Computer Science', 'PhD');

INSERT INTO users (email, password_hash, first_name, last_name, role, is_active)
VALUES ('student@grader.ua',
        '$2a$10$hSHVI9VER/QhOVDXPnSSk.4G0jgvXnGN5T2B9BfDnLobGFNzSQaS6',
        'Ivan', 'Petrenko', 'STUDENT', TRUE);

INSERT INTO students (user_id)
VALUES (currval('users_id_seq'));
