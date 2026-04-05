# University Grader CMS — Progress Tracker

## Project Overview
University CMS with automated code testing via GitLab CI/CD.
Stack: Spring Boot 3.2, Java 21, Hibernate/JPA, PostgreSQL,
Keycloak (SSO/JWT), GitLab CE (self-hosted), Docker sandbox, React SPA.

## Architecture
- Modular monolith (Spring Boot)
- 4 modules: Auth, Courses, CI/CD checker, Results
- DB: PostgreSQL (single DB, Flyway migrations)
- Auth: Keycloak (JWT validation via Spring Security)
- Code runner: GitLab CE → GitLab Runner → Docker container

## Database schema
[link to dbdiagram.io or paste key tables here]

## Sessions

### Session 1 — Project setup ⬜
- [ ] pom.xml dependencies verified
- [ ] application.yml: PostgreSQL connection
- [ ] Virtual threads enabled
- [ ] Flyway configured
- [ ] V1__init.sql: users, teachers, students, admins tables
- [ ] App starts, tables created in DB

### Session 2 — User entities ⬜
- [ ] JPA entities: User, Teacher, Student, Admin
- [ ] Repositories
- [ ] V2__courses.sql migration
- [ ] Basic test

### Session 3 — Course entities ⬜
- [ ] Course, CourseEnrollment, CourseTeacher
- [ ] Assignment, ProgrammingTask, FileUploadTask, Tests
- [ ] Repositories + service stubs

### Session 4 — Auth (Keycloak) ⬜
- [ ] Keycloak added to Docker Compose
- [ ] Spring Security JWT filter
- [ ] Role extraction: STUDENT, TEACHER, ADMIN
- [ ] Protected endpoint test

### Session 5 — Courses module ⬜
- [ ] Course CRUD endpoints
- [ ] Enrollment logic
- [ ] Assignment creation

### Session 6 — CI/CD module ⬜
- [ ] GitLab API client
- [ ] Push code + .gitlab-ci.yml on submission
- [ ] Webhook endpoint + signature validation
- [ ] Pipeline result → DB

### Session 7 — Results module ⬜
- [ ] GET /submissions/{id}/status (polling)
- [ ] GET /submissions/{id}/results
- [ ] Score calculation

### Session 8 — React frontend ⬜
- [ ] Vite + React + Axios + keycloak-js
- [ ] Login + route protection
- [ ] Course list, submission form, results page

## Blocked / Issues
_nothing yet_

## Key decisions
- No Keycloak until Session 4
- Polling (not SSE) for submission status
- Docker executor for C/C++ sandbox
- Virtual threads instead of WebFlux