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
- [x] V10 migration: `gitlab_project_id` added to submissions + indexes
- [x] `GitLabSubmissionService`: creates project, pushes solution + .gitlab-ci.yml, registers webhook, records pipeline id
- [x] `SubmissionServiceImpl.createSubmission()`: calls `triggerPipeline()` after save
- [x] `SubmissionServiceImpl.applyGitLabResult()`: maps status, fetches job logs, scores submission
- [x] `WebhookController` POST `/api/webhooks/gitlab`: verifies X-Gitlab-Token, ignores non-terminal statuses
- [x] `/api/webhooks/**` added to SecurityConfig permitAll

### Session 8 — React frontend ✅ (in progress, see grader-frontend/PROGRESS.md)
- [x] Auth (Keycloak login/logout), protected layout, Sidebar/Topbar
- [x] Course list page + CreateCourse dialog
- [x] Course detail page (parallel fetch, role-aware sections)
- [x] Assignment detail page: info card, submission form (student), submission list (teacher/admin)
- [x] Submission status page with usePolling (3 s interval, stops on terminal status)
- [x] SubmissionStatusBadge, SubmissionForm, SubmissionList, usePolling hook

### AI Test Gen — Phase 0: Environment setup ✅
- [x] GPU: RTX 3060 Laptop, 6 GB VRAM → model `qwen2.5-coder:3b`
- [x] Sandbox images `grader/sandbox/` (`cpp/Dockerfile`, `python/Dockerfile`, `build.sh`, `build.ps1`)
  - `grader-sandbox-cpp:1` (gcc:13 + gcovr 5.2), `grader-sandbox-py:1` (python:3.12-slim + pytest, pytest-cov)
  - Verified with `--network none`: passing sample exits 0, failing sample exits 1 (both languages)
- [x] `testgen.*` block in application.yml; `./mvnw -q compile` passes
- [x] "AI Test Generation" section in CLAUDE.MD
- [x] Ollama 0.35.0 installed, `qwen2.5-coder:3b` pulled (1.9 GB, runs 100% on GPU, default context 4096)
- [x] Smoke test `POST /api/chat` (pytest for `add(a, b)`): cold 93 s (model load 40 s), warm 1.1 s (~119 tok/s);
  generated 5 tests, all pass in `grader-sandbox-py:1`

### AI Test Gen — Phase 1A: Backend foundation ✅
- [x] V26 `reference_solution` on `programming_tasks`; `ProgrammingTask.update(...)` + `ProgrammingTaskDetails.referenceSolution`
  (nested in Create/UpdateAssignmentRequest, so no separate top-level field)
- [x] Reference solution hidden from students: `AssignmentResponse.from(a, includeReferenceSolution)` with
  `CurrentUser.isStaff()` in AssignmentServiceImpl + CourseServiceImpl; template DTOs always return null
- [x] V27 `test_generation_jobs` (config JSONB) + `test_generation_iterations`; entities, `JobStatus`, `PromptType`, repositories
- [x] `TestGenProperties` (`testgen.*`, plus `ollama.keep-alive`, `ollama.num-ctx`)
- [x] `OllamaClient.chat(...)` (RestClient, keep_alive/num_ctx/seed), `extractCode`, `LlmUnavailableException` → 503
- [x] `SandboxRunner.run/runWithCoverage` (single container run with phase markers, timeout + `docker kill`,
  hardening flags, pytest `-rA` / `PASS|FAIL` parsing, pytest-cov / gcovr coverage), `SandboxUnavailableException` → 503
- [x] Tests: SandboxRunnerTest (11 docker + 3 parsing), OllamaClientTest (6), CurrentUserTest (3),
  AssignmentServiceTest (+4 visibility/persistence); full suite 183 green
- [x] Verified: Flyway V26/V27 applied, `ddl-auto: validate` OK; live JSONB round-trip, Ollama chat 8.1 s warm,
  generated tests 5/5 in sandbox
- Note: template → course copy does not carry a reference solution (templates have no such field yet)

### Deployment — server stack (out of the box) ✅
- [x] `Dockerfile` (multi-stage, temurin 25, Docker CLI for sandboxes) + `.dockerignore` (excludes local secrets)
- [x] `compose.server.yaml`: postgres 16 (+ keycloak DB via init script), keycloak (postgres-backed, health check
  without curl), s3mock, gitlab + runner, ollama + one-shot model pull, sandbox image builds, backend;
  `compose.gpu.yaml` NVIDIA override; `.env.example`; `DEPLOY.md`
- [x] `SandboxRunner`: files streamed via stdin tar into tmpfs `/work` (no bind mount), runs as `nobody`
- [x] `minio.public-endpoint` for presigned links (backend uses internal `http://s3:9090`)
- [x] Verified locally under a separate project: all services healthy, Flyway 27 migrations, backend UP,
  sandbox run from inside the backend container, backend to Ollama reachable
- Open: frontend container + reverse proxy/TLS; Keycloak `start` mode for production

### AI Test Gen — Phase 1B: Self-repair loop, mutation feedback, API ✅
- [x] Prompts `resources/prompts/` (system, generate + per-language conventions, repair_compile, repair_failing,
  kill_mutant) via `PromptBuilder`/`TaskSpec`; reference solution never in prompts
- [x] `Mutator` (relational, arithmetic, constant ±1, boolean, return→0; skips comments/strings/#include),
  `MutantPoolBuilder` (batched syntax check, seeded A/B split), `MutantPool.leaksHeldOut` guard
- [x] `SandboxRunner.runAgainstMutants` (all mutants in one container, per-mutant timeout) + `checkSyntax`
- [x] `TestGenerationServiceImpl`: loop, anti-cheat guard, async jobs (virtual threads, max 2 concurrent),
  `runSync` for evaluation, startup recovery of interrupted jobs
- [x] `TestGenerationController`: POST 202 / GET job; 404, 403 (other teacher), 409 (one running job), 400 validation
- [x] V28 (held-out kills, coverage), V29 (temperature), V30 (PRUNE_FAILING prompt type)
- [x] Tests: MutatorTest 11, MutantPoolBuilderTest 3, PromptBuilderTest 7, TestPrunerTest 3,
  TestGenerationServiceImplTest 25, TestGenerationControllerTest 4, SandboxRunnerTest +3 docker; suite 238 green
- [x] E2E (qwen2.5-coder:3b, local GPU): Python fib SUCCEEDED (3 repairs stuck on one test → pruned to 7 valid
  tests, 3/3 feedback mutants killed); C++ reverse_string SUCCEEDED (6 tests, 4/4 mutants, 100% coverage)
- Deviations from the phase spec, driven by E2E observations with the 3b model:
  - Repairs without chat history (history made the model copy its last answer); task summary in each repair prompt
  - Temperature +0.4 after an unchanged repair (0.2 → 0.6 → 1.0)
  - pytest summary needed `COLUMNS=1000` (failure messages were truncated, so the model never saw the real error)
  - `import pytest` auto-added; PRUNE_FAILING step (configurable, for ablation in Phase 3)

---

## Blocked / Issues
_nothing currently blocked_

## Key decisions
- No Keycloak for MVP — custom JWT auth (simpler, self-contained)
- Polling (not SSE) for submission status
- Docker executor for C/C++ sandbox
- Virtual threads instead of WebFlux
- Soft-delete pattern for groups (is_active = false), not hard delete
