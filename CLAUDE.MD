# Grader — Automated Lab Submission and Grading System

## Project Overview
Grader is a web-based system for automated checking of student lab submissions
using GitLab CI/CD pipelines. Students upload code, it runs in an isolated
Docker container via GitLab CI, and results are returned through a REST API
consumed by a React frontend. So this is kind of clone of google class but with built-in code checker.

## Tech Stack
- Java 25, Spring Boot 3.4.x
- PostgreSQL 16, Flyway migrations
- React 18 + TypeScript + MUI v5
- Docker Compose for deployment
- GitLab CE (self-hosted) for CI/CD pipeline execution

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
- JWT secret loaded from env variable: GRADER_JWT_SECRET
- Token expiry: 24 hours
- Two roles: STUDENT, TEACHER
- Hardcoded users in data.sql for demo purposes

## Error Handling
- All exceptions handled in GlobalExceptionHandler (@RestControllerAdvice)
- Always return RFC 7807 Problem Details format:
  { "status": 404, "title": "Not Found", "detail": "Assignment 5 not found" }

## Testing
- Unit tests for all service classes
- Use Mockito for mocking repositories and external clients
- Test class naming: {ClassName}Test
- At minimum test: happy path + not found case + validation error case
