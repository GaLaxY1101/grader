---
name: java-code-quality
description: >
  Use this skill whenever writing, implementing, refactoring, or reviewing Java Spring Boot code — 
  even if the user just says "implement X", "add a service", "create an endpoint", "fix this class", 
  or "refactor this". This skill enforces DRY, SOLID, separation of concerns, and clean Spring Boot 
  architecture inline as code is generated. It blocks anti-patterns before they are written and 
  corrects them immediately. Always activate for any Java/Spring Boot coding task in this project, 
  whether it's a new feature, a bug fix, or a refactor.
---

# Java Spring Boot Code Quality Skill

You are writing production-grade Java Spring Boot code. Before writing a single line, think through the
design. Code that works but is poorly structured is not acceptable — catch architectural mistakes in your
own reasoning before they appear in the output.

---

## Pre-code checklist (run through this before writing)

Ask yourself these questions. If any answer is "no", fix the design first:

1. Does every class have exactly one reason to change?
2. Is business logic confined to the service layer?
3. Are there any duplicated blocks I could extract into a shared method or utility?
4. Am I depending on concrete classes where I should depend on interfaces?
5. Does this new class belong in an existing package, or do I need a new one?
6. Can I validate inputs at the boundary (controller/DTO) rather than deep in the service?

---

## Layer responsibilities — strict boundaries

Each layer has a single job. Never leak responsibilities across boundaries.

### Controller
- Handles HTTP only: maps routes, reads request body, returns `ResponseEntity`
- Zero business logic — every non-trivial operation is delegated to a service
- Validates incoming DTOs with `@Valid`; never validates manually
- Never calls a repository directly

```java
// CORRECT
@PostMapping
public ResponseEntity<SubmissionResponse> submit(
        @Valid @RequestBody SubmissionRequest request,
        @AuthenticationPrincipal UserDetails user) {
    return ResponseEntity.ok(submissionService.submit(request, user.getUsername()));
}

// BLOCKED — business logic in controller
@PostMapping
public ResponseEntity<SubmissionResponse> submit(@RequestBody SubmissionRequest req) {
    if (req.fileContent() == null || req.fileContent().isBlank()) { // validation belongs in DTO
        return ResponseEntity.badRequest().build();
    }
    var submission = new Submission(); // entity construction belongs in service
    submission.setContent(req.fileContent());
    submissionRepository.save(submission); // repository call belongs in service
    return ResponseEntity.ok(...);
}
```

### Service
- Owns all business logic and orchestration
- Returns plain domain objects or response DTOs — never `ResponseEntity`
- Calls repositories and external clients; never touches `HttpServletRequest`/`HttpServletResponse`
- Every public method has a Javadoc comment explaining what it does and why

```java
// CORRECT
/**
 * Creates a new submission for the given assignment, persists it, and triggers the GitLab pipeline.
 */
public SubmissionResponse submit(SubmissionRequest request, String username) { ... }

// BLOCKED — ResponseEntity in service
public ResponseEntity<SubmissionResponse> submit(...) { ... }
```

### Repository
- Data access only: queries, saves, deletes
- No business logic, no logging of business events, no external HTTP calls
- Use Spring Data JPA query methods or `@Query` — never hand-write JDBC unless unavoidable

### Entity
- Represents the database row — nothing more
- No service calls, no formatting logic, no HTTP concerns
- Relationships declared with `@ManyToOne`, `@OneToMany`, etc. — lazy by default

---

## DRY — eliminate duplication before it accumulates

Duplication is the silent killer of maintainability. When you notice the same logic appearing twice, stop and extract it.

**Common duplication traps to watch for:**

| Trap | Fix |
|------|-----|
| Same query with slightly different filters in two services | Move to repository with a parameter |
| Same validation logic in two controllers | Move to DTO annotation or a shared validator |
| Same entity-to-DTO mapping in multiple places | Extract a `toResponse()` method or a mapper class |
| Repeated `entityRepo.findById(id).orElseThrow(...)` calls | Extract a private `findOrThrow(id)` helper in the service |

```java
// BLOCKED — same lookup duplicated everywhere
public void updateSubmission(Long id, ...) {
    Submission s = submissionRepository.findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Submission " + id + " not found"));
    ...
}
public SubmissionResponse getSubmission(Long id) {
    Submission s = submissionRepository.findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Submission " + id + " not found"));
    ...
}

// CORRECT — extracted helper
private Submission findSubmissionOrThrow(Long id) {
    return submissionRepository.findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Submission " + id + " not found"));
}
```

---

## SOLID — applied concretely

### Single Responsibility
Each class does one thing. If you can describe a class with "and", split it.

- `SubmissionService` orchestrates submissions — it does NOT also parse GitLab logs
- `LogParser` parses CI output — it does NOT also persist results
- `GitLabApiClient` talks to the GitLab API — it does NOT also build pipeline configs

### Open/Closed
Extend behavior through new classes, not by modifying existing ones.

- Adding a new assignment type → create a new handler, don't add an `if` branch to the existing service
- Adding a new notification channel → implement a `NotificationSender` interface, don't modify `SubmissionService`

### Liskov Substitution
If you create an interface, every implementation must honor the full contract — no silent no-ops or unexpected exceptions from implementations.

### Interface Segregation
Don't create fat interfaces. If a class only needs two methods from a five-method interface, split the interface.

### Dependency Inversion
Depend on the interface, not the concrete class. Spring's DI makes this natural — inject interfaces, not implementations.

```java
// CORRECT
private final SubmissionRepository submissionRepository; // interface
private final GitLabApiClient gitLabApiClient;           // interface or abstraction

// BLOCKED — coupling to concrete class across module boundary
private final GitLabApiClientImpl gitLabApiClientImpl;
```

---

## Constructor injection only

Never use `@Autowired` on fields. Always use constructor injection (Lombok `@RequiredArgsConstructor` is the standard here).

```java
// CORRECT
@Service
@RequiredArgsConstructor
public class SubmissionService {
    private final SubmissionRepository submissionRepository;
    private final AssignmentRepository assignmentRepository;
    private final GitLabApiClient gitLabApiClient;
}

// BLOCKED
@Service
public class SubmissionService {
    @Autowired
    private SubmissionRepository submissionRepository; // field injection
}
```

---

## DTOs are records

All request and response DTOs must be Java records, not classes.

```java
// CORRECT
public record SubmissionRequest(
    @NotNull Long assignmentId,
    @NotBlank String fileContent
) {}

// BLOCKED
public class SubmissionRequest {
    private Long assignmentId;
    private String fileContent;
    // getters, setters, constructor...
}
```

---

## Error handling

- Never catch `Exception` or `RuntimeException` generically — catch the specific type
- Never swallow exceptions silently (catch with empty body)
- Throw meaningful exceptions that `GlobalExceptionHandler` can map to RFC 7807 responses
- Don't add try/catch for conditions that simply can't happen

```java
// CORRECT
try {
    gitLabApiClient.triggerPipeline(projectId);
} catch (GitLabApiException e) {
    throw new PipelineTriggerException("Failed to trigger pipeline for project " + projectId, e);
}

// BLOCKED
try {
    gitLabApiClient.triggerPipeline(projectId);
} catch (Exception e) {  // too broad
    log.error("error", e);
    // swallowed — caller has no idea it failed
}
```

---

## Optional usage

Never call `.get()` on an `Optional` without first checking `.isPresent()`. Prefer `.orElseThrow()` with a meaningful exception, or `.ifPresent()` / `.map()` for transformations.

```java
// CORRECT
return submissionRepository.findById(id)
    .orElseThrow(() -> new EntityNotFoundException("Submission " + id + " not found"));

// BLOCKED
Optional<Submission> opt = submissionRepository.findById(id);
return opt.get(); // NPE waiting to happen
```

---


Don't put things in `common/` just because you're not sure. `common/` is for truly cross-cutting utilities.

---

## Self-correction protocol

When writing code, if you catch yourself about to:

- Write business logic in a controller → **stop**, move it to a service method
- Duplicate a lookup or validation already written elsewhere → **stop**, extract a shared helper
- Add `@Autowired` on a field → **stop**, use constructor injection
- Return `ResponseEntity` from a service → **stop**, return the domain/DTO type instead
- Catch `Exception` → **stop**, identify and catch the specific exception type
- Call `.get()` on an Optional → **stop**, use `.orElseThrow()` or `.map()`
- Add logic to an entity → **stop**, move it to the service

Flag the issue inline with a comment like `// REFACTORED: moved from controller — business logic belongs here` so the user can see what decision was made.
