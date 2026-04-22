-- Seed: 4 additional students, 5 courses, enrollments, assignments
-- Teacher: teacher@grader.ua (Olena Kovalenko)

-- ── Additional students ────────────────────────────────────────────────────────

INSERT INTO users (email, password_hash, first_name, last_name, role, is_active)
VALUES ('anna.shevchenko@grader.ua',
        '$2a$10$tltHDKfl8Bm6iOKHhkZPgO1jA4.xbu0C77iL24HVGOJomt1LTVLRa',
        'Anna', 'Shevchenko', 'STUDENT', TRUE);
INSERT INTO students (user_id) VALUES (currval('users_id_seq'));

INSERT INTO users (email, password_hash, first_name, last_name, role, is_active)
VALUES ('mykola.bondarenko@grader.ua',
        '$2a$10$tltHDKfl8Bm6iOKHhkZPgO1jA4.xbu0C77iL24HVGOJomt1LTVLRa',
        'Mykola', 'Bondarenko', 'STUDENT', TRUE);
INSERT INTO students (user_id) VALUES (currval('users_id_seq'));

INSERT INTO users (email, password_hash, first_name, last_name, role, is_active)
VALUES ('daryna.moroz@grader.ua',
        '$2a$10$tltHDKfl8Bm6iOKHhkZPgO1jA4.xbu0C77iL24HVGOJomt1LTVLRa',
        'Daryna', 'Moroz', 'STUDENT', TRUE);
INSERT INTO students (user_id) VALUES (currval('users_id_seq'));

INSERT INTO users (email, password_hash, first_name, last_name, role, is_active)
VALUES ('artem.savchenko@grader.ua',
        '$2a$10$tltHDKfl8Bm6iOKHhkZPgO1jA4.xbu0C77iL24HVGOJomt1LTVLRa',
        'Artem', 'Savchenko', 'STUDENT', TRUE);
INSERT INTO students (user_id) VALUES (currval('users_id_seq'));

-- ── 5 courses (created_by = teacher Olena Kovalenko) ──────────────────────────

INSERT INTO courses (name, description, created_by, academic_year, semester, start_date, end_date, is_active)
SELECT 'Algorithms and Data Structures',
       'Fundamental algorithms, complexity analysis, sorting, graphs, dynamic programming.',
       t.id, 2025, 2, '2025-02-10', '2025-06-15', TRUE
FROM teachers t JOIN users u ON t.user_id = u.id WHERE u.email = 'teacher@grader.ua';

INSERT INTO courses (name, description, created_by, academic_year, semester, start_date, end_date, is_active)
SELECT 'Operating Systems',
       'Process management, memory, file systems, concurrency and synchronisation.',
       t.id, 2025, 2, '2025-02-10', '2025-06-15', TRUE
FROM teachers t JOIN users u ON t.user_id = u.id WHERE u.email = 'teacher@grader.ua';

INSERT INTO courses (name, description, created_by, academic_year, semester, start_date, end_date, is_active)
SELECT 'Database Systems',
       'Relational model, SQL, normalisation, transactions, query optimisation.',
       t.id, 2025, 2, '2025-02-10', '2025-06-15', TRUE
FROM teachers t JOIN users u ON t.user_id = u.id WHERE u.email = 'teacher@grader.ua';

INSERT INTO courses (name, description, created_by, academic_year, semester, start_date, end_date, is_active)
SELECT 'Web Programming',
       'HTML/CSS, JavaScript, REST APIs, React basics, deployment.',
       t.id, 2025, 2, '2025-02-10', '2025-06-15', TRUE
FROM teachers t JOIN users u ON t.user_id = u.id WHERE u.email = 'teacher@grader.ua';

INSERT INTO courses (name, description, created_by, academic_year, semester, start_date, end_date, is_active)
SELECT 'Computer Networks',
       'OSI model, TCP/IP, routing, DNS, HTTP, network security fundamentals.',
       t.id, 2025, 2, '2025-02-10', '2025-06-15', TRUE
FROM teachers t JOIN users u ON t.user_id = u.id WHERE u.email = 'teacher@grader.ua';

-- ── Link teacher to all 5 courses in course_teachers ──────────────────────────

INSERT INTO course_teachers (course_id, teacher_id, role)
SELECT c.id, t.id, 'LECTURER'
FROM courses c
         JOIN teachers t ON TRUE
         JOIN users u ON t.user_id = u.id
WHERE u.email = 'teacher@grader.ua'
  AND c.created_by = t.id
ON CONFLICT DO NOTHING;

-- ── Enroll all 5 students in all 5 courses ────────────────────────────────────

INSERT INTO course_enrollments (course_id, student_id, status)
SELECT c.id, s.id, 'ACTIVE'
FROM courses c
         CROSS JOIN students s
         JOIN teachers t ON c.created_by = t.id
         JOIN users u ON t.user_id = u.id
WHERE u.email = 'teacher@grader.ua'
ON CONFLICT DO NOTHING;

-- ── Assignments — 3 per course ────────────────────────────────────────────────

-- Algorithms and Data Structures
INSERT INTO assignments (course_id, title, description, max_score, deadline, created_by)
SELECT c.id,
       'Lab 1 — Sorting Algorithms',
       'Implement quicksort, mergesort and heapsort. Compare performance on random and sorted input.',
       100, '2025-03-10 23:59:00+02', t.id
FROM courses c JOIN teachers t ON c.created_by = t.id JOIN users u ON t.user_id = u.id
WHERE u.email = 'teacher@grader.ua' AND c.name = 'Algorithms and Data Structures';

INSERT INTO assignments (course_id, title, description, max_score, deadline, created_by)
SELECT c.id,
       'Lab 2 — Graph Traversal',
       'Implement BFS and DFS. Find shortest path in an unweighted graph.',
       100, '2025-04-07 23:59:00+02', t.id
FROM courses c JOIN teachers t ON c.created_by = t.id JOIN users u ON t.user_id = u.id
WHERE u.email = 'teacher@grader.ua' AND c.name = 'Algorithms and Data Structures';

INSERT INTO assignments (course_id, title, description, max_score, deadline, created_by)
SELECT c.id,
       'Lab 3 — Dynamic Programming',
       'Solve longest common subsequence and knapsack problems using DP.',
       100, '2025-05-05 23:59:00+02', t.id
FROM courses c JOIN teachers t ON c.created_by = t.id JOIN users u ON t.user_id = u.id
WHERE u.email = 'teacher@grader.ua' AND c.name = 'Algorithms and Data Structures';

-- Operating Systems
INSERT INTO assignments (course_id, title, description, max_score, deadline, created_by)
SELECT c.id,
       'Lab 1 — Shell Scripting',
       'Write bash scripts to automate file management and process monitoring tasks.',
       100, '2025-03-17 23:59:00+02', t.id
FROM courses c JOIN teachers t ON c.created_by = t.id JOIN users u ON t.user_id = u.id
WHERE u.email = 'teacher@grader.ua' AND c.name = 'Operating Systems';

INSERT INTO assignments (course_id, title, description, max_score, deadline, created_by)
SELECT c.id,
       'Lab 2 — Process Scheduling Simulation',
       'Simulate FIFO, SJF and Round-Robin scheduling algorithms in C.',
       100, '2025-04-14 23:59:00+02', t.id
FROM courses c JOIN teachers t ON c.created_by = t.id JOIN users u ON t.user_id = u.id
WHERE u.email = 'teacher@grader.ua' AND c.name = 'Operating Systems';

INSERT INTO assignments (course_id, title, description, max_score, deadline, created_by)
SELECT c.id,
       'Lab 3 — Multithreading',
       'Producer-consumer problem using POSIX threads and semaphores.',
       100, '2025-05-12 23:59:00+02', t.id
FROM courses c JOIN teachers t ON c.created_by = t.id JOIN users u ON t.user_id = u.id
WHERE u.email = 'teacher@grader.ua' AND c.name = 'Operating Systems';

-- Database Systems
INSERT INTO assignments (course_id, title, description, max_score, deadline, created_by)
SELECT c.id,
       'Lab 1 — ER Modelling',
       'Design an ER diagram for a library management system and generate DDL.',
       100, '2025-03-24 23:59:00+02', t.id
FROM courses c JOIN teachers t ON c.created_by = t.id JOIN users u ON t.user_id = u.id
WHERE u.email = 'teacher@grader.ua' AND c.name = 'Database Systems';

INSERT INTO assignments (course_id, title, description, max_score, deadline, created_by)
SELECT c.id,
       'Lab 2 — Advanced SQL',
       'Write complex queries using joins, window functions, CTEs and subqueries.',
       100, '2025-04-21 23:59:00+02', t.id
FROM courses c JOIN teachers t ON c.created_by = t.id JOIN users u ON t.user_id = u.id
WHERE u.email = 'teacher@grader.ua' AND c.name = 'Database Systems';

INSERT INTO assignments (course_id, title, description, max_score, deadline, created_by)
SELECT c.id,
       'Lab 3 — Transactions and Isolation',
       'Demonstrate dirty reads, non-repeatable reads and phantom reads with different isolation levels.',
       100, '2025-05-19 23:59:00+02', t.id
FROM courses c JOIN teachers t ON c.created_by = t.id JOIN users u ON t.user_id = u.id
WHERE u.email = 'teacher@grader.ua' AND c.name = 'Database Systems';

-- Web Programming
INSERT INTO assignments (course_id, title, description, max_score, deadline, created_by)
SELECT c.id,
       'Lab 1 — Responsive Layout',
       'Build a responsive portfolio page using HTML5 and CSS Flexbox/Grid.',
       100, '2025-03-31 23:59:00+02', t.id
FROM courses c JOIN teachers t ON c.created_by = t.id JOIN users u ON t.user_id = u.id
WHERE u.email = 'teacher@grader.ua' AND c.name = 'Web Programming';

INSERT INTO assignments (course_id, title, description, max_score, deadline, created_by)
SELECT c.id,
       'Lab 2 — REST API Client',
       'Fetch data from a public REST API and render it dynamically using vanilla JS.',
       100, '2025-04-28 23:59:00+02', t.id
FROM courses c JOIN teachers t ON c.created_by = t.id JOIN users u ON t.user_id = u.id
WHERE u.email = 'teacher@grader.ua' AND c.name = 'Web Programming';

INSERT INTO assignments (course_id, title, description, max_score, deadline, created_by)
SELECT c.id,
       'Lab 3 — React SPA',
       'Build a single-page application with React: routing, state management, API integration.',
       100, '2025-05-26 23:59:00+02', t.id
FROM courses c JOIN teachers t ON c.created_by = t.id JOIN users u ON t.user_id = u.id
WHERE u.email = 'teacher@grader.ua' AND c.name = 'Web Programming';

-- Computer Networks
INSERT INTO assignments (course_id, title, description, max_score, deadline, created_by)
SELECT c.id,
       'Lab 1 — Packet Analysis',
       'Capture and analyse HTTP and DNS traffic using Wireshark.',
       100, '2025-03-17 23:59:00+02', t.id
FROM courses c JOIN teachers t ON c.created_by = t.id JOIN users u ON t.user_id = u.id
WHERE u.email = 'teacher@grader.ua' AND c.name = 'Computer Networks';

INSERT INTO assignments (course_id, title, description, max_score, deadline, created_by)
SELECT c.id,
       'Lab 2 — Socket Programming',
       'Implement a TCP echo server and client in Python.',
       100, '2025-04-14 23:59:00+02', t.id
FROM courses c JOIN teachers t ON c.created_by = t.id JOIN users u ON t.user_id = u.id
WHERE u.email = 'teacher@grader.ua' AND c.name = 'Computer Networks';

INSERT INTO assignments (course_id, title, description, max_score, deadline, created_by)
SELECT c.id,
       'Lab 3 — Network Security',
       'Set up TLS on a simple HTTPS server; analyse certificate chain and cipher suites.',
       100, '2025-05-12 23:59:00+02', t.id
FROM courses c JOIN teachers t ON c.created_by = t.id JOIN users u ON t.user_id = u.id
WHERE u.email = 'teacher@grader.ua' AND c.name = 'Computer Networks';
