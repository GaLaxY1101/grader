-- Fix seed user password hashes (previous hashes did not match the documented passwords)
-- admin@grader.ua   → Admin123!
-- teacher@grader.ua → Teacher123!
-- student@grader.ua → Student123!

UPDATE users SET password_hash = '$2a$10$hUjOL0l6DWiap2H/QlwNL.zUbwdKocGTh0g4PWp2UVhqzv8DIk.2a' WHERE email = 'admin@grader.ua';
UPDATE users SET password_hash = '$2a$10$pnytkEL7aCYYKsEw78j.0.xzBYECitRwB7cLBl9igvgTeK..Gdtf6' WHERE email = 'teacher@grader.ua';
UPDATE users SET password_hash = '$2a$10$tltHDKfl8Bm6iOKHhkZPgO1jA4.xbu0C77iL24HVGOJomt1LTVLRa' WHERE email = 'student@grader.ua';
