# Grader — Automated Lab Submission and Grading System

## Project Overview
Grader is a web-based system for automated checking of student lab submissions
using GitLab CI/CD pipelines. Students upload code, it runs in an isolated
Docker container via GitLab CI, and results are returned through a REST API
consumed by a React frontend. So this is kind of clone of google class but with built-in code checker.

## Local Dev

PostgreSQL credentials (from compose.yaml):
- host: localhost:5432, user: illia, password: graderpass123, database: grader-db
- Connect: `PGPASSWORD=graderpass123 psql -h localhost -U illia -d grader-db`

Run the app: `GRADER_JWT_SECRET=test-secret-at-least-32-chars-long ./mvnw spring-boot:run`

Server deployment: `compose.server.yaml` + `.env` (see `DEPLOY.md`). `compose.yaml` is the dev stack only.
Keep their compose project names different (`grader` vs `grader-server`).

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

## AI Test Generation
Teachers can generate unit tests with an LLM; generated tests are validated by running them in
Docker sandboxes (see `docs/ai-testgen/` in the monorepo root). Code lives in package
`ua.kpi.grader.testgen`.

### Provider switch
`testgen.provider` picks the LLM backend. Two `LlmClient` beans (`GeminiClient`, `OllamaClient`)
are guarded by `@ConditionalOnProperty`, so exactly one is active per boot.

| Provider | When to use | Required env |
|---|---|---|
| `gemini` (default) | Production + anywhere without a GPU | `GEMINI_API_KEY` |
| `ollama` | Local dev with a GPU, offline runs, thesis baseline | Running Ollama server + pulled model |

Switch via `TESTGEN_PROVIDER=gemini|ollama` in `.env`. Model tag goes in `TESTGEN_MODEL`
(defaults: `gemini-3.8-flash` for gemini, `qwen2.5-coder:3b` for ollama via `OLLAMA_MODEL`).

### Gemini (default)
- Create an API key at https://aistudio.google.com/apikey and put it in `.env` as `GEMINI_API_KEY`.
- Free tier: `gemini-2.0-flash` ≈ 15 RPM / 1500 RPD; `gemini-3.8-flash` ≈ 10 RPM / 500 RPD.
- `GEMINI_THINKING_BUDGET` (2.5 only; `0` disables thinking for fastest responses).
- No local resources; the backend reaches `generativelanguage.googleapis.com` on `:443` outbound.
- No native `seed` parameter — reproducibility relies on temperature + the stagnation heuristic.

### Ollama (opt-in)
- Install from https://ollama.com and keep it running (`ollama serve`, default port 11434).
- Pull the configured model once: `ollama pull qwen2.5-coder:3b`
  (3b chosen for a 6 GB GPU; use `qwen2.5-coder:7b` with >= 8 GB VRAM via `OLLAMA_MODEL`).
- Smoke test: `curl http://localhost:11434/api/chat -d '{"model":"qwen2.5-coder:3b","stream":false,"messages":[{"role":"user","content":"hi"}]}'`
- On the server stack, Ollama services sit behind the `ollama` Compose profile:
  `docker compose -f compose.server.yaml --profile ollama up -d --build`.

### Sandbox images
Generated tests run in `docker run --rm --network none ...`, so all tooling is baked into the images.
- Server: built automatically by `compose.server.yaml` (services `sandbox-cpp`, `sandbox-python`); see `DEPLOY.md`.
- Local dev: `./sandbox/build.sh` (Linux/macOS/Git Bash) or `.\sandbox\build.ps1` (PowerShell).
- `grader-sandbox-cpp:1` — `gcc:13` + `gcovr`; runs `g++ -std=c++17 -DGRADER_MAIN -o test_runner test.cpp && ./test_runner`.
- `grader-sandbox-py:1` — `python:3.12-slim` + `pytest`, `pytest-cov`; runs `pytest -q test_solution.py`.
- `SandboxRunner` streams `solution.*` + test file into the container over stdin (tar) into a tmpfs at
  `/work`. No bind mounts, so it also works when the backend itself runs in a container using the
  host's `/var/run/docker.sock`. Bump the image tag when changing a Dockerfile.
- Hardening: `--user 65534:65534`, `--read-only`, `--cap-drop ALL`, `--pids-limit 64`,
  memory/CPU limits, output capped at 256 KB. The grader harness is copied next to the test file;
  results come from its `##GRADER##` events, with `PASS <name>` / `FAIL <name>: ...` lines as the legacy fallback.
- Docker-backed tests are tagged `@Tag("docker")` and skip automatically when the images are missing.
- `CompilationService` (compile check for teachers/students) also runs in the sandbox via
  `SandboxRunner.compileOnly`; never run compilers in the backend process (secrets in its env).

### Self-repair loop (`TestGenerationServiceImpl`)
- Iteration 0 = GENERATE (spec only, never the reference solution). Then up to `max-iterations` repairs:
  REPAIR_COMPILE → REPAIR_FAILING → KILL_MUTANT (diff of a surviving feedback-set mutant).
- Mutants (`Mutator`, `MutantPoolBuilder`): split by seed into set A (feedback) and set B (held-out,
  never in prompts; guarded by `MutantPool.leaksHeldOut`). Every candidate records A/B kills + coverage.
- Repairs are sent without chat history (system + self-contained prompt); an unchanged answer raises the
  next call's temperature by 0.4. Python files using `pytest.` get `import pytest` added (`normalize`).
- Anti-cheat: a candidate with fewer than `min-test-count` tests and fewer than before is rejected.
- PRUNE_FAILING (no LLM call) runs last when enabled. Every iteration is stored in `test_generation_iterations`.
- API: `POST /api/test-generation` (202 + jobId), `GET /api/test-generation/{jobId}`; TEACHER/ADMIN,
  owner-only (ADMIN sees all), one PENDING/RUNNING job per user (409). `runSync(...)` = same loop, no DB.
- Jobs left RUNNING by a restart are marked FAILED on startup (`InterruptedJobRecovery`).

### Configuration (`testgen.*` in application.yml)
| Property | Default | Meaning |
|---|---|---|
| `testgen.provider` | `${TESTGEN_PROVIDER:gemini}` | Active LLM backend: `gemini` or `ollama` |
| `testgen.gemini.base-url` | `${GEMINI_BASE_URL:https://generativelanguage.googleapis.com}` | Gemini API base URL |
| `testgen.gemini.api-key` | `${GEMINI_API_KEY:}` | Google AI Studio key; required when provider is `gemini` |
| `testgen.gemini.model` | `${TESTGEN_MODEL:gemini-3.8-flash}` | Gemini model tag |
| `testgen.gemini.timeout-seconds` | `60` | HTTP timeout for one Gemini call |
| `testgen.gemini.max-output-tokens` | `8192` | Upper bound on tokens generated per call |
| `testgen.gemini.thinking-budget` | `${GEMINI_THINKING_BUDGET:0}` | 2.5 only; `0` disables thinking |
| `testgen.ollama.base-url` | `${OLLAMA_BASE_URL:http://localhost:11434}` | Ollama server URL |
| `testgen.ollama.model` | `${OLLAMA_MODEL:qwen2.5-coder:3b}` | Ollama model tag |
| `testgen.ollama.timeout-seconds` | `180` | HTTP timeout for one Ollama call |
| `testgen.ollama.keep-alive` | `30m` | How long Ollama keeps the model loaded (avoids ~90 s cold loads) |
| `testgen.ollama.num-ctx` | `8192` | Context window; repair prompts carry conversation history |
| `testgen.temperature` | `0.2` | Sampling temperature |
| `testgen.max-iterations` | `3` | Max self-repair iterations (0 = single-shot) |
| `testgen.min-test-count` | `5` | Minimum tests required; repairs dropping below are rejected |
| `testgen.mutation-feedback` | `true` | Feed surviving mutants back to the LLM |
| `testgen.max-mutants` | `10` | Mutants generated per reference solution |
| `testgen.prune-failing` | `true` | After the loop, drop tests still failing on the reference (if ≥ min-test-count remain) |
| `testgen.sandbox.cpp-image` | `grader-sandbox-cpp:1` | C/C++ sandbox image |
| `testgen.sandbox.python-image` | `grader-sandbox-py:1` | Python sandbox image |
| `testgen.sandbox.timeout-seconds` | `20` | Wall-clock limit per sandbox run |
| `testgen.sandbox.mutant-timeout-seconds` | `5` | Limit per mutant when running tests against mutants |
| `testgen.sandbox.memory` | `256m` | Container memory limit |
| `testgen.sandbox.cpus` | `"1"` | Container CPU limit |

## Structured Test Feedback
Students see per-test results (expected/got) instead of a raw CI log. Plan: `docs/test-feedback/PLAN.md`
in the monorepo root.
- Harness (`src/main/resources/harness/`): `grader_test.h` (C/C++, `TEST_CASE` / `EXPECT_EQ` / `EXPECT_TRUE` /
  `EXPECT_NEAR`, `main()` via `-DGRADER_MAIN`) and `conftest.py` (pytest plugin; tests use
  `assert actual == expected`). `GitLabSubmissionService` commits the harness with every attempt;
  `SandboxRunner` copies it into the AI test-generation sandbox.
- Event protocol: one JSON per line prefixed `##GRADER## ` — `plan`, `start`, `result`, `end` from the harness,
  `compile_failed` / `timeout` from the CI script.
- `submission/feedback/TestReportParser` (pure, no Spring) turns a job log into a `TestReport`; derives
  CRASHED (start without result), NOT_RUN, TIMEOUT; falls back to PASS/FAIL and pytest summary lines.
- Stored in `attempt_test_results` + `attempts.tests_passed/tests_total/test_run_status/compile_output` (V31).
- `programming_tasks.feedback_level` (V32): FULL / NAMES_ONLY / SUMMARY for students; staff always get FULL.
  Students below FULL also lose `compileOutput`. Students get `pipelineOutput` only as a fallback when there is
  no structured report (legacy tests, custom CI) and the level is FULL; staff always get it.
- A successful pipeline is downgraded to FAILED when the harness report shows a non-passing test or no
  `end` event (e.g. `exit(0)` in student code).
- Known limitation: student code shares the process with the harness and can print fake events; see the plan.
