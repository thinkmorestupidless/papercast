# Tasks: Scheduled Podcast Creation

**Input**: Design documents from `specs/003-podcast-schedule/`
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/ ✅

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1–US3)
- Include exact file paths in descriptions

---

## Phase 1: Foundational (Blocking Prerequisites)

**Purpose**: Domain record and Key Value Entity — required by all user story phases before any can begin.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T001 Create `PodcastSchedule` domain record with fields `scheduleId`, `userId`, `searchTerms`, `cadence` (`Cadence` enum: `DAILY`/`WEEKLY`/`MONTHLY`), `timeHour` (int), `timeMinute` (int), `dayOfWeek` (`java.time.DayOfWeek`, nullable), `dayOfMonth` (`Integer`, nullable), `status` (`Status` enum: `ACTIVE`/`PAUSED`), `createdAt`, `lastModifiedAt`, `nextRunAt` (nullable), `recentRuns` (`List<ScheduleRun>`); nested record `ScheduleRun(String runId, Instant triggeredAt, String workflowId, String workflowStatusUrl)`; static factory `create(scheduleId, userId, searchTerms, cadence, timeHour, timeMinute, dayOfWeek, dayOfMonth, Instant now, Instant nextRunAt)`; pure domain method `nextRunAfter(Instant from)` computing next UTC trigger for each cadence (DAILY: next HH:MM after `from`; WEEKLY: next dayOfWeek+HH:MM after `from`; MONTHLY: next dayOfMonth+HH:MM after `from`, skip months shorter than dayOfMonth); mutation helpers `withPaused()` (sets status=PAUSED, nextRunAt=null), `withResumed(Instant nextRunAt)` (sets status=ACTIVE), `withUpdated(searchTerms, cadence, timeHour, timeMinute, dayOfWeek, dayOfMonth, Instant nextRunAt, Instant now)`, `withRunRecorded(ScheduleRun run, Instant nextRunAt)` (prepend run, trim list to 100 entries, set nextRunAt) in `src/main/java/com/example/domain/PodcastSchedule.java`

- [X] T002 [P] Create `PodcastScheduleTest` pure unit tests (no Akka dependencies) for `nextRunAfter()`: DAILY same-day future returns same-day time; DAILY past time returns next-day time; WEEKLY same-week future returns correct day+time; WEEKLY past returns next-week occurrence; MONTHLY same-month future returns correct day; MONTHLY short month skips correctly (day 28 in a month with 28 days goes to next month); `withRunRecorded()` caps list at 100 newest entries (101st oldest is dropped) in `src/test/java/com/example/domain/PodcastScheduleTest.java`

- [X] T003 Create `PodcastScheduleEntity` Key Value Entity annotated `@Component(id = "podcast-schedule")` extending `KeyValueEntity<PodcastSchedule>`; inner records: `CreateSchedule(String userId, String searchTerms, PodcastSchedule.Cadence cadence, int timeHour, int timeMinute, java.time.DayOfWeek dayOfWeek, Integer dayOfMonth, Instant nextRunAt)`, `ScheduleCreatedResponse(String scheduleId, Instant nextRunAt)`, `ResumeSchedule(Instant nextRunAt)`, `UpdateSchedule(String searchTerms, PodcastSchedule.Cadence cadence, int timeHour, int timeMinute, java.time.DayOfWeek dayOfWeek, Integer dayOfMonth, Instant nextRunAt)`, `RecordRun(String runId, Instant triggeredAt, String workflowId, String workflowStatusUrl, Instant nextRunAt)`; command handlers: `createSchedule(CreateSchedule)` → `Effect<ScheduleCreatedResponse>` (error if state already exists; validates searchTerms non-blank, timeHour 0-23, timeMinute 0-59, dayOfMonth 1-28 if MONTHLY, dayOfWeek/dayOfMonth null if not applicable cadence; creates state via `PodcastSchedule.create(commandContext().entityId(), ...)`), `pauseSchedule()` → `Effect<Done>` (error if null or already PAUSED), `resumeSchedule(ResumeSchedule)` → `Effect<Done>` (error if null or already ACTIVE), `updateSchedule(UpdateSchedule)` → `Effect<Done>` (error if null; same field validations as create), `deleteSchedule()` → `Effect<Done>` (sets state to null via `effects().deleteEntity()`), `recordRun(RecordRun)` → `Effect<Done>` (no-op returning `Done` if state is null; otherwise calls `state.withRunRecorded(...)`), `getSchedule()` → `Effect<PodcastSchedule>` (error if null) in `src/main/java/com/example/application/PodcastScheduleEntity.java`

- [X] T004 [P] Create `PodcastScheduleEntityTest` unit tests using `KeyValueEntityTestKit<PodcastSchedule, PodcastScheduleEntity>`; test cases: `createSchedule` on new entity → state is ACTIVE with correct fields; `createSchedule` on existing entity → error; `pauseSchedule` on ACTIVE → state status is PAUSED and nextRunAt is null; `pauseSchedule` on PAUSED → error; `resumeSchedule` on PAUSED → state status is ACTIVE; `resumeSchedule` on ACTIVE → error; `updateSchedule` on existing → searchTerms/cadence fields updated; `updateSchedule` on non-existent → error; `deleteSchedule` on existing → entity state is null; `recordRun` on ACTIVE entity → run prepended to recentRuns list; `recordRun` on null state → returns `Done` without error; `getSchedule` on non-existent → error in `src/test/java/com/example/application/PodcastScheduleEntityTest.java`

**Checkpoint**: Run `mvn test -Dtest=PodcastScheduleTest,PodcastScheduleEntityTest` — all unit tests must pass before proceeding.

---

## Phase 2: User Story 1 — Create and Activate a Podcast Schedule (Priority: P1) 🎯 MVP

**Goal**: Authenticated users can create a recurring schedule; the system automatically produces a podcast at each configured UTC time.

**Independent Test**: `POST /schedules` with `{"searchTerms":"machine learning","cadence":"WEEKLY","timeHour":9,"timeMinute":0,"dayOfWeek":"MONDAY"}` → 201 with `scheduleId` and `nextRunAt`. `GET /schedules` returns the schedule in the list.

- [X] T005 [US1] Create `SchedulesByOwnerView` extending `View`; inner static class `ScheduleRows` extending `TableUpdater<ScheduleSummary>` annotated `@Consume.FromKeyValueEntity(PodcastScheduleEntity.class)` with `onUpdate(PodcastSchedule state)` handler (returns `effects().deleteRow()` if state is null, otherwise `effects().updateRow(new ScheduleSummary(...))`); inner records `ScheduleSummary(String scheduleId, String userId, String searchTerms, String cadence, String status, Instant nextRunAt, Instant createdAt)` and `ScheduleSummaries(List<ScheduleSummary> schedules)`; query method `getByOwner(String userId)` annotated `@Query("SELECT * AS schedules FROM schedule_rows WHERE userId = :userId ORDER BY createdAt DESC")` returning `QueryEffect<ScheduleSummaries>` in `src/main/java/com/example/application/SchedulesByOwnerView.java`

- [X] T006 [US1] Create `PodcastScheduleTrigger` extending `TimedAction` annotated `@Component(id = "podcast-schedule-trigger")`; inject `ComponentClient componentClient` and `TimerScheduler timerScheduler` via constructor; single method `trigger(String scheduleId)` returning `Effect`: (1) fetch state via `componentClient.forKeyValueEntity(scheduleId).method(PodcastScheduleEntity::getSchedule).invoke()`, catching any exception treating it as null; (2) if state is null or `status == PAUSED`, return `effects().done()`; (3) generate `workflowId = UUID.randomUUID().toString()` and `runId = UUID.randomUUID().toString()`; (4) start workflow: `componentClient.forWorkflow(workflowId).method(PodcastCreationWorkflow::create).invoke(new PodcastCreationWorkflow.CreateCommand(state.searchTerms()))`; (5) compute `Instant now = Instant.now()` and `Instant nextRunAt = state.nextRunAfter(now)`; (6) record run: `componentClient.forKeyValueEntity(scheduleId).method(PodcastScheduleEntity::recordRun).invoke(new RecordRun(runId, now, workflowId, "/podcast/"+workflowId+"/status", nextRunAt))`; (7) schedule next timer: `timerScheduler.createSingleTimer("podcast-schedule-"+scheduleId, Duration.between(now, nextRunAt), componentClient.forTimedAction().method(PodcastScheduleTrigger::trigger).deferred(scheduleId))`; (8) return `effects().done()` in `src/main/java/com/example/application/PodcastScheduleTrigger.java`

- [X] T007 [US1] Create `PodcastScheduleEndpoint` annotated `@HttpEndpoint("/schedules")`, `@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))`, `@JWT(validate = JWT.JwtMethodMode.BEARER_TOKEN)`, extending `AbstractHttpEndpoint`; inject `ComponentClient componentClient` and `TimerScheduler timerScheduler` via constructor; add private helper `extractUserId()` returning `requestContext().getJwtClaims().subject().orElseThrow(() -> HttpException.badRequest("Missing sub claim"))`; add private helper `String timerName(String scheduleId)` returning `"podcast-schedule-" + scheduleId`; add inner records `CreateScheduleRequest(String searchTerms, String cadence, int timeHour, int timeMinute, String dayOfWeek, Integer dayOfMonth)`, `UpdateScheduleRequest(String searchTerms, String cadence, int timeHour, int timeMinute, String dayOfWeek, Integer dayOfMonth)`, `RunsResponse(List<PodcastSchedule.ScheduleRun> runs)`; implement `@Post createSchedule(CreateScheduleRequest)` returning `akka.http.javadsl.model.HttpResponse`: (1) extractUserId; (2) validate searchTerms non-blank → 400; validate cadence parseable → 400; validate timeHour 0-23, timeMinute 0-59 → 400; validate dayOfWeek non-null for WEEKLY, dayOfMonth 1-28 for MONTHLY → 400; (3) query view `componentClient.forView().method(SchedulesByOwnerView::getByOwner).invoke(userId)` — reject with 400 if count ≥ 10; (4) generate `scheduleId = UUID.randomUUID().toString()`; build a temporary `PodcastSchedule` to call `nextRunAfter(Instant.now())`; (5) call `componentClient.forKeyValueEntity(scheduleId).method(PodcastScheduleEntity::createSchedule).invoke(new CreateSchedule(userId, ..., nextRunAt))`; (6) compute `delay = Duration.between(Instant.now(), nextRunAt)` and call `timerScheduler.createSingleTimer(timerName(scheduleId), delay, componentClient.forTimedAction().method(PodcastScheduleTrigger::trigger).deferred(scheduleId))`; (7) return `HttpResponses.created(response)`; implement `@Get listSchedules()` returning `SchedulesByOwnerView.ScheduleSummaries` via view query in `src/main/java/com/example/api/PodcastScheduleEndpoint.java`

- [X] T008 [US1] Create `PodcastScheduleEndpointIntegrationTest` extending `TestKitSupport`; add `bearerTokenWith(Map<String, Object> claims)` helper (same pattern as `UserAccountEndpointIntegrationTest`: unsigned JWT with base64 header + base64 JSON claims); add static `USER_A_ID = "auth0|sched-a-" + UUID.randomUUID()`, `USER_B_ID = "auth0|sched-b-" + UUID.randomUUID()`; helpers `validTokenA()`, `validTokenB()` with `sub`, `email_verified: "true"`, `name`, `email`; add US1 tests: POST with valid DAILY body → 201 with non-blank `scheduleId` and non-null `nextRunAt`; POST with blank searchTerms → 400; POST with MONTHLY cadence and `dayOfMonth = 29` → 400; POST with WEEKLY and missing dayOfWeek → 400; GET /schedules → 200 and list contains created scheduleId in `src/test/java/com/example/api/PodcastScheduleEndpointIntegrationTest.java`

**Checkpoint**: Run `mvn verify -Dtest=PodcastScheduleEndpointIntegrationTest` — US1 tests must pass.

---

## Phase 3: User Story 2 — Manage Existing Schedules (Priority: P2)

**Goal**: Authenticated users can pause, resume, update, and delete their schedules, and list all schedules with current status and next run time.

**Independent Test**: POST schedule → PATCH /pause → GET shows PAUSED → PATCH /resume → GET shows ACTIVE.

- [X] T009 [US2] Add `@Get("/{id}") getSchedule(String scheduleId)` method to `PodcastScheduleEndpoint` that fetches full state via `componentClient.forKeyValueEntity(scheduleId).method(PodcastScheduleEntity::getSchedule).invoke()`, verifies `state.userId().equals(extractUserId())` returning 404 if mismatch, returns state; add `@Patch("/{id}/pause") pauseSchedule(String scheduleId)` that fetches state (ownership check + 404), calls `timerScheduler.cancel(timerName(scheduleId))`, calls `componentClient.forKeyValueEntity(scheduleId).method(PodcastScheduleEntity::pauseSchedule).invoke()`, wraps entity error (already paused) as `HttpException.badRequest(...)`, returns `HttpResponses.ok()`; add `@Patch("/{id}/resume") resumeSchedule(String scheduleId)` that fetches state (ownership check + 404), computes `nextRunAt = state.nextRunAfter(Instant.now())`, calls entity `resumeSchedule(new ResumeSchedule(nextRunAt))`, wraps entity error (already active) as 400, creates new timer, returns `HttpResponses.ok()` in `src/main/java/com/example/api/PodcastScheduleEndpoint.java`

- [X] T010 [US2] Add `@Put("/{id}") updateSchedule(String scheduleId, UpdateScheduleRequest request)` method to `PodcastScheduleEndpoint` that validates request fields (same rules as create), fetches state (ownership check + 404), computes `nextRunAt`, calls entity `updateSchedule(new UpdateSchedule(..., nextRunAt))`, cancels old timer via `timerScheduler.cancel(timerName(scheduleId))`, creates new timer with updated delay, returns `HttpResponses.ok()`; add `@Delete("/{id}") deleteSchedule(String scheduleId)` that fetches state (ownership check + 404), cancels timer, calls entity `deleteSchedule()`, returns `HttpResponses.ok()` in `src/main/java/com/example/api/PodcastScheduleEndpoint.java`

- [X] T011 [US2] Add US2 integration tests to `PodcastScheduleEndpointIntegrationTest`: GET /schedules/{id} for own schedule → 200 with schedule data; GET /schedules/{id} for user B's schedule by user A → 404; PATCH /schedules/{id}/pause → 200; GET shows status PAUSED; PATCH /schedules/{id}/pause again → 400 (already paused); PATCH /schedules/{id}/resume → 200; GET shows status ACTIVE; PATCH /schedules/{id}/resume again → 400 (already active); PUT /schedules/{id} with updated searchTerms → 200; GET reflects new searchTerms; DELETE /schedules/{id} → 200; GET /schedules/{id} → 404 in `src/test/java/com/example/api/PodcastScheduleEndpointIntegrationTest.java`

- [X] T012 [US2] Add schedule-limit integration test to `PodcastScheduleEndpointIntegrationTest`: create 10 schedules for user B → each returns 201; create 11th → 400; DELETE one of the 10; create again → 201 (limit no longer exceeded) in `src/test/java/com/example/api/PodcastScheduleEndpointIntegrationTest.java`

**Checkpoint**: Run `mvn verify -Dtest=PodcastScheduleEndpointIntegrationTest` — all US1 + US2 tests must pass.

---

## Phase 4: User Story 3 — View Schedule Execution History (Priority: P3)

**Goal**: Authenticated users can retrieve the execution history for a specific schedule.

**Independent Test**: GET /schedules/{id}/runs on a newly created schedule → 200 with empty `runs` list.

- [X] T013 [US3] Add `@Get("/{id}/runs") getScheduleRuns(String scheduleId)` method to `PodcastScheduleEndpoint` that fetches full schedule state via `PodcastScheduleEntity::getSchedule`, performs ownership check (404 if mismatch or null), wraps `state.recentRuns()` in `RunsResponse` and returns it in `src/main/java/com/example/api/PodcastScheduleEndpoint.java`

- [X] T014 [US3] Add US3 integration tests to `PodcastScheduleEndpointIntegrationTest`: GET /schedules/{id}/runs on new schedule → 200 with empty runs list; GET /schedules/{id}/runs for another user's schedule → 404 in `src/test/java/com/example/api/PodcastScheduleEndpointIntegrationTest.java`

**Checkpoint**: Run `mvn verify -Dtest=PodcastScheduleEndpointIntegrationTest` — all tests must pass.

---

## Phase 5: Polish & Cross-Cutting Concerns

- [X] T015 Run `mvn verify` (full suite including all integration tests) and confirm zero test failures in project root

---

## Dependencies & Execution Order

### Phase Dependencies

- **Foundational (Phase 1)**: No dependencies — start immediately
- **US1 (Phase 2)**: Depends on Phase 1 completion (T001, T003 must be done)
- **US2 (Phase 3)**: Depends on Phase 2 completion (adds methods to same endpoint file)
- **US3 (Phase 4)**: Depends on Phase 2 completion (adds one method to same endpoint file)
- **Polish (Phase 5)**: Depends on all phases complete

### User Story Dependencies

- **US1 (P1)**: Can start after Foundational — no story dependencies
- **US2 (P2)**: Depends on US1 (T009/T010 add to the endpoint created in T007; T011/T012 add to test file created in T008)
- **US3 (P3)**: Depends on US1 (T013 adds to T007's endpoint; T014 adds to T008's test file); can start in parallel with US2 from a code perspective but same files means sequential

### Within Each Phase

- T001 and T003 must be sequential (T003 imports PodcastSchedule from T001)
- T002 [P] and T003 can overlap (different files; T002 only needs T001)
- T004 [P] can start as soon as T003 is done (different file from T005, T006)
- T005 [P] and T006 [P] can run in parallel after T003 (different files)
- T007 must wait for T005 and T006 (endpoint uses View and TimedAction)
- T009 and T010 must be sequential (same file, T010 depends on T009's ownership-check pattern)
- T011 and T012 must be sequential (same test file; T012 builds on T011's setup)

---

## Parallel Opportunities

```bash
# Phase 1 parallelism:
Once T001 is done:
  Task A: T003 — Create PodcastScheduleEntity (depends on T001)
  Task B: T002 — Create PodcastScheduleTest (depends on T001, different file from T003)

# Once T003 is done:
  Task A: T004 — Create PodcastScheduleEntityTest (depends on T003)
  Task B: T005 — Create SchedulesByOwnerView (depends on T003)
  Task C: T006 — Create PodcastScheduleTrigger (depends on T003)

# T007 waits for T005 + T006 to complete
```

---

## Implementation Strategy

### MVP First (US1 Only)

1. Complete Phase 1: Foundational (T001–T004)
2. Complete Phase 2: US1 — create schedule + list (T005–T008)
3. **STOP and VALIDATE**: POST /schedules creates a schedule; GET /schedules lists it; blank searchTerms rejected; schedule limit enforced
4. Deploy / demo if ready

### Incremental Delivery

1. Foundational → verify unit tests pass (T001–T004)
2. US1 → users can create recurring schedules; trigger fires the pipeline (T005–T008)
3. US2 → full lifecycle management: pause, resume, update, delete (T009–T012)
4. US3 → execution history visible per schedule (T013–T014)

---

## Notes

- `PodcastScheduleTest` and `PodcastScheduleEntityTest` use no TestKit; name them `*Test.java` so Surefire runs them during `mvn test`
- `PodcastScheduleEndpointIntegrationTest` extends `TestKitSupport`; name it `*IntegrationTest.java` so Failsafe runs it during `mvn verify`
- `TimerScheduler` in the TestKit environment does **not** auto-fire timers — integration tests validate schedule CRUD only; timer execution can be tested manually via the `trigger(scheduleId)` shortcut in `quickstart.md` scenario 4
- `PodcastScheduleTrigger.trigger()` calls `getSchedule()` which errors on null state — wrap in try-catch and treat exception as "schedule gone, stop chain"
- `deleteSchedule()` uses `effects().deleteEntity()` (KVE); the View's `onUpdate` handler receives null state on deletion and calls `effects().deleteRow()`
- The `dayOfWeek` / `dayOfMonth` fields in `CreateScheduleRequest` arrive as Strings over HTTP; parse them to `DayOfWeek` and `Integer` in the endpoint handler and convert to the appropriate typed fields before forwarding to the entity
- Ownership check pattern: always fetch entity state first; if state is null or `state.userId()` ≠ authenticated user, throw `HttpException.notFound()` — never leak whether a schedule exists for another user
