# University Grader CMS — Progress Tracker

## Project Overview
University CMS with automated code testing via GitLab CI/CD.
Stack: Spring Boot 3.4.x, Java 25, Hibernate/JPA, PostgreSQL,
JWT (custom, no Keycloak for MVP), GitLab CE (self-hosted), Docker sandbox, React 18 + MUI v5.

## Architecture
- Modular monolith (Spring Boot), package-per-feature structure
- DB: PostgreSQL 16, Flyway migrations
- Auth: Keycloak OAuth2 Resource Server — validates Keycloak JWTs, roles from `app_roles` claim
- Code runner: GitLab CE → GitLab Runner → Docker container
- Error handling: RFC 7807 Problem Details via `GlobalExceptionHandler`

## Database schema
Tables created so far:
- `users`, `teachers`, `students`, `admins` (V1)
- seed data: hardcoded demo users (V2)
- removed GitLab fields from profiles (V3)
- `academic_groups`, `group_students` (V4)
- `courses`, `course_enrollments`, `course_teachers`, `assignments`, `programming_tasks`, `file_upload_tasks` (V5)

---

## Sessions

### Session 1 — Project setup ✅
- [x] pom.xml dependencies verified (Spring Boot 3.4.x, Lombok, Flyway, etc.)
- [x] application.yml: PostgreSQL connection, virtual threads, Flyway
- [x] V1__init_users.sql: users, teachers, students, admins tables
- [x] V2__seed_users.sql: hardcoded demo users
- [x] V3__remove_gitlab_fields_from_profiles.sql
- [x] App starts, tables created in DB

### Session 2 — User management ✅
- [x] JPA entities: User, Teacher, Student, Admin
- [x] Repositories: UserRepository, TeacherRepository, StudentRepository
- [x] Services: UserService, TeacherService, StudentService
- [x] Controllers: UserController (`/api/v1/users`), TeacherController (`/api/v1/teachers`), StudentController (`/api/v1/students`)
- [x] Full CRUD for users, teachers, students
- [x] GlobalExceptionHandler: 404 ResourceNotFoundException, 409 DuplicateEmailException, 400 validation
- [x] Unit tests: UserServiceTest, UserRepositoryTest

### Session 3 — Group management ✅
- [x] V4__init_groups.sql: `academic_groups`, `group_students` tables
- [x] JPA entities: AcademicGroup, GroupStudent
- [x] Repositories: AcademicGroupRepository, GroupStudentRepository (join-fetch query)
- [x] GroupService with full business logic (soft-delete, duplicate checks, 409 guard)
- [x] GroupController (`/api/groups`): 8 endpoints
  - POST   /api/groups                          → create (201)
  - GET    /api/groups                          → list active (200)
  - GET    /api/groups/{id}                     → get by id (200/404)
  - PUT    /api/groups/{id}                     → update (200)
  - DELETE /api/groups/{id}                     → deactivate, is_active=false (204)
  - POST   /api/groups/{id}/students/{sid}      → add student (201)
  - DELETE /api/groups/{id}/students/{sid}      → remove student (204)
  - GET    /api/groups/{id}/students            → list students (200)
- [x] Unit tests: GroupServiceTest (18 tests — happy path, 404, 409 for all ops)

### Session 4 — Auth (Keycloak JWT) ✅
- [x] Replaced custom JJWT auth with Keycloak OAuth2 resource server (`spring-boot-starter-oauth2-resource-server`)
- [x] `KeycloakJwtRoleConverter`: maps `app_roles` JWT claim → Spring Security `ROLE_*` authorities
- [x] `SecurityConfig`: stateless, CSRF disabled, CORS for `localhost:3000`, `@EnableMethodSecurity`, OAuth2 resource server with JWT converter
- [x] `CurrentUser` component: extracts userId (Keycloak UUID), email, name from JWT via SecurityContext
- [x] `@PreAuthorize` on all endpoints — GET → STUDENT/TEACHER/ADMIN, POST/PUT/DELETE → TEACHER/ADMIN
- [x] Services inject `CurrentUser` for `createCourse` / `createAssignment` — teacher resolved by JWT email claim
- [x] `GlobalExceptionHandler` handles `AccessDeniedException` (403) and `AuthenticationException` (401)
- [x] `spring-boot-starter-actuator` added; `/actuator/health` public (200), all other endpoints require Keycloak JWT
- [x] Verified: unauthenticated → 401, invalid token → 401, health → 200, api-docs → 200
- [ ] Full integration test with real Keycloak token (when Keycloak runs in Docker Compose)

### Session 5 — Course + Assignment module ✅
- [x] V5__init_courses_and_assignments.sql: 6 tables (courses, course_enrollments, course_teachers, assignments, programming_tasks, file_upload_tasks)
- [x] JPA entities: Course, CourseEnrollment, CourseTeacher, Assignment, ProgrammingTask, FileUploadTask
- [x] Repositories: CourseRepository, CourseEnrollmentRepository, CourseTeacherRepository, AssignmentRepository, ProgrammingTaskRepository, FileUploadTaskRepository
- [x] CourseService / CourseServiceImpl: full CRUD + enrollment + teacher management
- [x] AssignmentService / AssignmentServiceImpl: full CRUD, supports simple / programming / file-upload types
- [x] CourseController (`/api/courses`): 11 endpoints
  - POST   /api/courses                              → create (201)
  - GET    /api/courses                              → list active (200)
  - GET    /api/courses/{id}                         → detail with students, teachers, assignments (200/404)
  - PUT    /api/courses/{id}                         → update (200)
  - DELETE /api/courses/{id}                         → soft delete is_active=false (204)
  - POST   /api/courses/{id}/students/{studentId}    → enroll student (201)
  - DELETE /api/courses/{id}/students/{studentId}    → drop student, status=DROPPED (204)
  - GET    /api/courses/{id}/students                → list students (200)
  - POST   /api/courses/{id}/teachers/{teacherId}    → add teacher (201)
  - DELETE /api/courses/{id}/teachers/{teacherId}    → remove teacher (204)
  - GET    /api/courses/{id}/teachers                → list teachers (200)
- [x] AssignmentController: 5 endpoints
  - POST   /api/courses/{courseId}/assignments       → create + optional task details (201)
  - GET    /api/courses/{courseId}/assignments       → list active (200)
  - GET    /api/assignments/{id}                     → get by id with task details (200/404)
  - PUT    /api/assignments/{id}                     → update fields (200)
  - DELETE /api/assignments/{id}                     → soft delete (204)
- [x] Assignment type via optional child records: programmingTask / fileUploadTask — no type enum or column
- [x] 409 on duplicate enrollment / duplicate teacher add
- [x] Soft-delete pattern: is_active=false for courses and assignments
- [x] Bug fixes (code review): null-guard optional fields in `Course.update()` and `Assignment.update()`, enrollment list filtered to `ACTIVE` only, `@NotNull @Min(1)` on `UpdateAssignmentRequest.maxScore`
- [x] `createCourse` / `createAssignment` now resolve teacher from authenticated JWT principal (no hardcoded IDs)
- [x] Unit tests: CourseServiceTest (15 tests), AssignmentServiceTest (10 tests)

### Session 6 — Submissions module ✅
- [x] V9__init_submissions.sql: `submissions` table (status, code_content, score, pipeline fields, indexes)
- [x] `SubmissionStatus` enum: PENDING / RUNNING / PASSED / FAILED / ERROR
- [x] `Submission` entity with `startPipeline()` and `applyResult()` mutation methods
- [x] `SubmissionRepository`: fetch-join queries (avoids N+1), list by assignment, find by assignment+student
- [x] `StudentRepository.findByUser_Email()` added (mirrors TeacherRepository pattern)
- [x] `SubmissionService` / `SubmissionServiceImpl`: createSubmission, findById, getStatus, listByAssignment, getMyLatestSubmission
- [x] `SubmissionController` (5 endpoints):
  - POST   /api/assignments/{id}/submissions       → submit (STUDENT/TEACHER/ADMIN, 201)
  - GET    /api/submissions/{id}                   → full detail (STUDENT=own only, TEACHER/ADMIN=any)
  - GET    /api/submissions/{id}/status            → lightweight poll (same access rules)
  - GET    /api/assignments/{id}/submissions       → all for assignment (TEACHER/ADMIN)
  - GET    /api/assignments/{id}/submissions/my    → student's latest (STUDENT, 204 if none)
- [x] Unit tests: SubmissionServiceTest (11 tests — happy path, not-found, ownership denial)

### Session 7 — GitLab CI/CD integration 🔧
- [x] GitLab CE + GitLab Runner added to compose.yaml (grader-network, named volumes)
- [x] `gitlab:` config block in application.yml (baseUrl, token, groupName, webhookSecret, runnerToken)
- [x] `GitLabProperties` config record + `@EnableConfigurationProperties` in GraderApplication
- [x] `GitLabApiClient`: RestClient + JdkClientHttpRequestFactory (getOrCreateGroup, createProject, pushFile, registerWebhook, getPipeline, getJobLog, getPipelineJobs)
- [x] DTOs: `GitLabProjectDto`, `GitLabPipelineDto`, `GitLabJobDto`, `GitLabWebhookPayload`
- [x] `CiConfigService`: generates .gitlab-ci.yml for C/C++ (custom template override supported)
- [x] `GitLabInitializer`: auto-creates 'grader' group on startup — verified, group id=2
- [x] Local secrets via `application-local.properties` (gitignored), `spring-dotenv` removed
- [ ] Push code + .gitlab-ci.yml on submission (Part B)
- [ ] Webhook endpoint: POST /api/webhooks/gitlab → update status + score (Part B)

### Session 8 — React frontend ✅ (in progress, see grader-frontend/PROGRESS.md)
- [x] Auth (Keycloak login/logout), protected layout, Sidebar/Topbar
- [x] Course list page + CreateCourse dialog
- [x] Course detail page (parallel fetch, role-aware sections)
- [x] Assignment detail page: info card, submission form (student), submission list (teacher/admin)
- [x] Submission status page with usePolling (3 s interval, stops on terminal status)
- [x] SubmissionStatusBadge, SubmissionForm, SubmissionList, usePolling hook

---

## Blocked / Issues
_nothing currently blocked_

## Key decisions
- No Keycloak for MVP — custom JWT auth (simpler, self-contained)
- Polling (not SSE) for submission status
- Docker executor for C/C++ sandbox
- Virtual threads instead of WebFlux
- Soft-delete pattern for groups (is_active = false), not hard delete
