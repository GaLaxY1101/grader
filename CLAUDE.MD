# Verity — Automated Lab Submission and Grading System

## Project Overview
Verity is a web-based system for automated checking of student lab submissions
using GitLab CI/CD pipelines. Students upload code, it runs in an isolated
Docker container via GitLab CI, and results are returned through a REST API
consumed by a React frontend.

## Tech Stack
- Java 25, Spring Boot 3.4.x
- PostgreSQL 16, Flyway migrations
- React 18 + TypeScript + MUI v5
- Docker Compose for deployment
- GitLab CE (self-hosted) for CI/CD pipeline execution

## Package Structure
ua.kpi.verity/
auth/         → JWT utils, security config, login endpoint
user/         → User entity, repository
assignment/   → Assignment entity, repo, service, controller
submission/   → Submission entity, repo, service, controller
gitlab/       → GitLabApiClient, WebhookHandler, LogParser
result/       → TestResult entity, repository
common/       → GlobalExceptionHandler, base DTOs, constants

## Code Conventions
- All code, comments, and Javadoc in English
- Constructor injection only — never @Autowired on fields
- All request/response DTOs are Java records
- No business logic in controllers — delegate to services only
- Controllers return ResponseEntity, services never do
- Never catch generic Exception — catch specific exceptions only
- Every public service method must have a Javadoc comment
- Use @RequiredArgsConstructor from Lombok for constructor injection
- Validate all incoming DTOs with @Valid + Jakarta Validation annotations
- Use Optional properly — never call .get() without .isPresent() check

## Database Conventions
- Flyway migrations in resources/db/migration/
- Migration naming: V{number}__{snake_case_description}.sql
- All PKs use BIGSERIAL
- All timestamps use TIMESTAMP WITH TIME ZONE
- Foreign keys always have explicit names: fk_{table}_{referenced_table}

## Security
- JWT-based auth, no Keycloak for MVP
- JWT secret loaded from env variable: VERITY_JWT_SECRET
- Token expiry: 24 hours
- Two roles: STUDENT, TEACHER
- Hardcoded users in data.sql for demo purposes

## GitLab Integration
- Self-hosted GitLab at URL from env variable: GITLAB_URL
- API token from env variable: GITLAB_TOKEN
- Webhook secret from env variable: GITLAB_WEBHOOK_SECRET
- Each submission creates/updates a dedicated GitLab project
- CI config template stored in assignments.ci_config_template column
- Pipeline results parsed by finding lines starting with PASS: or FAIL:

## Error Handling
- All exceptions handled in GlobalExceptionHandler (@RestControllerAdvice)
- Always return RFC 7807 Problem Details format:
  { "status": 404, "title": "Not Found", "detail": "Assignment 5 not found" }

## Testing
- Unit tests for all service classes
- Use Mockito for mocking repositories and external clients
- Test class naming: {ClassName}Test
- At minimum test: happy path + not found case + validation error case

## MVP Scope (do not add anything beyond this)
- Login (student + teacher)
- Student: view assignments, upload solution file, view test results
- Teacher: view all submissions per assignment, see pass/fail per student
- GitLab pipeline trigger and result parsing
- No Keycloak, no complexity analysis, no academic groups management
```