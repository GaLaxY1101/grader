# Bulk Student Import — Progress Tracker

Feature: Teachers can import a class of students into an `AcademicGroup`
via manual row entry, `.xlsx` upload with a teacher-configurable column
mapping, or a mix of both. Two-phase flow: **parse** (returns editable
rows to the UI) then **commit** (transactional create + link).

Full design: `C:\Users\korni\.claude\plans\eventual-puzzling-kurzweil.md`
(also accessible from any Claude Code session in this repo).

Work top-to-bottom — each phase depends on the previous. Tick boxes as
you go; PR small vertical slices where possible.

## Phase 1 — Backend foundation (parsing + phone normalization) ✅

- [x] Add `libphonenumber` dependency to `pom.xml`
      (`com.googlecode.libphonenumber:libphonenumber:8.13.50`)
- [x] Create `common/util/PhoneNumberNormalizer.java` (`@Component`)
- [x] Create `common/exception/InvalidPhoneNumberException.java`
- [x] Create `common/exception/MissingColumnException.java`
- [x] Create `common/exception/InvalidImportException.java`
      (carries `List<RowError>`)
- [x] Wire all three new exceptions in
      `common/exception/GlobalExceptionHandler.java`
      (extended error DTOs `BulkImportErrorResponse` +
      `MissingColumnErrorResponse` carry `errors` / `missingColumn` /
      `detectedHeaders` — codebase uses `ErrorResponse` records, not
      RFC 7807 Problem Details, so extended siblings were the natural fit)
- [x] `PhoneNumberNormalizerTest` — every UA format round-trips to E.164,
      invalid input throws, blank returns null (20/20 green)

## Phase 2 — Backend DTOs ✅

- [x] `user/dto/StudentColumnMapping.java`
- [x] `user/dto/StudentInput.java`
- [x] `user/dto/ParsedStudentRow.java`
- [x] `user/dto/ParsedStudentsResponse.java`
- [x] `group/dto/BulkCommitRequest.java`
- [x] `group/dto/BulkCommitWithGroupRequest.java`
- [x] `group/dto/BulkImportResult.java` (+ nested `ImportedStudent`)

## Phase 3 — Backend reader ✅

- [x] `user/service/StudentXlsxReader.java` with `readHeaders()` and `read()`
- [x] `StudentXlsxReaderTest` — headers returned; custom mapping resolves
      (`E-mail`, `First Name`, `Surname`, `Телефон`); missing required
      header throws `MissingColumnException`; blank rows skipped;
      case-insensitive + whitespace-trimmed matching; phone column mapped
      normalises to E.164; phone mapping null → all rows `phone=null`;
      bad phone → row-level error with raw value preserved (10/10 green)

## Phase 4 — Backend orchestrator + endpoints ✅

- [x] `group/service/GroupBulkImportService.java` with `parseFile`,
      `commitIntoGroup`, `commitWithNewGroup`
- [x] `GroupBulkImportServiceTest` (7 tests, all green)
  - commit happy all-new users
  - commit mixed with `linked` for existing users
  - idempotent skip when already a member of target group
  - pre-validation catches duplicate-in-batch
  - pre-validation catches student already active in another group
    (correctly ignores the case where their active group IS the target)
  - pre-validation catches bad phone in edited-input path
  - pre-validation catches bean-validation errors
- [x] `group/controller/GroupBulkImportController.java` with three endpoints
  - `POST /api/groups/bulk-import/parse` (multipart)
  - `POST /api/groups/bulk-import` (JSON, create group + commit)
  - `POST /api/groups/{id}/students/bulk-import` (JSON, existing group)
  - all `@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")`
  - multipart endpoint validates `.xlsx` MIME/extension → 415 otherwise
- [ ] Verify OpenAPI spec at `http://localhost:8080/v3/api-docs` shows
      all three new endpoints with correct request/response schemas
      (manual — run server and eyeball; deferred to Phase 9)

**Full backend suite: 150/150 green.**

## Phase 5 — Frontend types + template ✅ (partial)

- [x] `cd grader-frontend && pnpm generate-api`
- [x] Confirmed generated types cover `ParsedStudentsResponse`,
      `BulkCommitRequest`, `BulkCommitWithGroupRequest`, `BulkImportResult`,
      `StudentColumnMapping`, `StudentInput`, `ImportedStudent`,
      `ParsedStudentRow`
- [ ] Place `students-template.xlsx` in `grader-frontend/public/`
      (canonical headers `email`, `firstName`, `lastName`, `phone`,
      one sample row) — **deferred**, teachers can name any columns
      via the mapping form; add later as a nicety

## Phase 6 — Frontend "Import students" screen (structure) ✅

- [x] Route `/admin/bulk-import` + "Bulk Import" button in `GroupsTab`
- [x] Group selector card
  - toggle new vs existing (custom radio-in-card visual)
  - new group inline form (code / faculty / speciality / year)
  - existing group dropdown backed by `GET /api/groups`, filtered by
    `isActive`
- [x] Editable students table
  - columns `#`, `Email *`, `First name *`, `Last name *`, `Phone`,
    `Status`, delete action
  - inline validation (email format, non-blank required fields,
    duplicate detection within batch)
  - per-row status chip: `Valid` (green) / `Error` (red + tooltip listing
    problems) / `Empty` (outlined)
  - single-row delete + Clear all
- [x] Add row button
- [x] Sticky bottom action bar with row counters
      (`N rows`, `X valid`, `Y with errors`) + Create button
      disabled while invalid
- [ ] Template download link — deferred alongside static template file

## Phase 7 — Frontend "Import from Excel" dialog ✅

- [x] `ImportFromFileDialog` component with `.xlsx`-only file input +
      four mapping fields (email/firstName/lastName/phone-optional)
- [x] Merge-mode radio (Replace / Append), default computed from
      current row count (Replace when empty, Append otherwise)
- [x] Hand-crafted multipart POST via `parseStudentsFile()` in
      `lib/api/bulkImport.ts` — reuses the NextAuth session token,
      pulls the response type from the generated client
- [x] Parsed rows merged into the table with server-side errors and
      raw phone values preserved for inline editing
- [x] 400 `MissingColumnException` → inline Alert shows the missing
      header AND the detected-header list as clickable Chips —
      clicking a chip auto-fills the mapping field

## Phase 8 — Frontend commit flow ✅

- [x] Wire Create button to `commitBulkImportWithNewGroup` /
      `commitBulkImportIntoGroup` in `lib/api/bulkImport.ts`
- [x] Success toast with `N students (X new, Y linked)` counts,
      then navigate back to `/admin` and refresh server components
- [x] 400 `InvalidImportException` → per-row `serverErrors` populated
      on the affected rows (visible via the Status chip tooltip),
      top-level banner "Could not create students…"
- [x] Unexpected error → top-level Alert with the error message
- [ ] Visual polish pass with `frontend-design` skill — deferred;
      current UI uses the existing PageHeader + Card pattern and is
      consistent with `AdminPanel` / `ManageGroupStudentsDialog`

**Frontend `pnpm tsc:test` + `pnpm build` both green. Route
`/admin/bulk-import` present in the build output.**

## Phase 9 — End-to-end verification (manual — teacher to run) ⏳

Local dev pre-req: Keycloak + Postgres up via `docker compose up`
(already running), backend up with
`GRADER_JWT_SECRET=test-secret-at-least-32-chars-long ./mvnw spring-boot:run`
(already running from this session — kill with the process manager
if you need to stop it), frontend up with
`cd grader-frontend && pnpm dev`.

Then navigate to **Admin → Groups → Bulk Import**
(`http://localhost:3000/admin/bulk-import`).

- [ ] Happy path: new group, mixed-format `.xlsx` with 3 rows including
      one existing seed email → 2 created + 1 linked
- [ ] Verify Postgres phones stored E.164:
      `PGPASSWORD=graderpass123 psql -h localhost -U illia -d grader-db -c "SELECT email, phone FROM users ORDER BY id DESC LIMIT 5;"`
- [ ] Verify new users appear in Keycloak admin console (each row has a
      `keycloak_id` UUID in DB, matching the Keycloak user ID)
- [ ] Verify Keycloak sent `UPDATE_PASSWORD` email — open Mailpit at
      http://localhost:8025, click the reset link, set a password,
      log in via the frontend as that student
- [ ] Bogus mapping (`Email` → `"e_mail"`) → missing-column dialog with
      detected headers (chips are clickable — click one to auto-fill)
- [ ] Unparseable phone in one row → row error, Create disabled
- [ ] Duplicate email in batch → 400 banner, no DB writes
- [ ] Student already active in another group → 400 banner naming
      that group, no DB writes
- [ ] Existing-group flow (Add to existing group) end-to-end
- [ ] Manual row entry only (no file upload) end-to-end

## Phase 10 — Cleanup

- [ ] Update `CLAUDE.md` if any new module-level convention emerged
      (e.g. how per-row error DTOs are surfaced in Problem Details)
- [ ] Short entry in `PROGRESS.md`
- [ ] Delete or archive this file once shipped
