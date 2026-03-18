# Data Model: Scheduled Podcast Creation

**Feature**: `003-podcast-schedule`
**Updated**: 2026-03-18

---

## Overview

The scheduling feature introduces a single primary aggregate, `PodcastSchedule`, managed as a Key Value Entity. Each schedule belongs to exactly one user and drives recurring invocations of the existing podcast creation pipeline. Execution history is stored inline within the aggregate (bounded to the last 100 runs). A view projects schedule data per user. A Timed Action fires at each scheduled interval and a HTTP Endpoint exposes the API.

---

## Domain Records

### `PodcastSchedule`

**Package**: `com.example.domain`

| Field | Type | Nullable | Description |
|---|---|---|---|
| `scheduleId` | `String` | No | UUID; entity key |
| `userId` | `String` | No | Auth0 sub claim, e.g. `auth0|abc123` |
| `searchTerms` | `String` | No | Non-blank string used as podcast search input |
| `cadence` | `Cadence` | No | `DAILY`, `WEEKLY`, or `MONTHLY` |
| `timeHour` | `int` | No | UTC hour of trigger, 0–23 |
| `timeMinute` | `int` | No | UTC minute of trigger, 0–59 |
| `dayOfWeek` | `DayOfWeek` | Conditional | Non-null for `WEEKLY`; must be null for `DAILY` and `MONTHLY` |
| `dayOfMonth` | `Integer` | Conditional | Non-null for `MONTHLY` (1–28); must be null for `DAILY` and `WEEKLY` |
| `status` | `Status` | No | `ACTIVE` or `PAUSED` |
| `createdAt` | `Instant` | No | Set at creation; never mutated |
| `lastModifiedAt` | `Instant` | No | Updated on every mutation |
| `nextRunAt` | `Instant` | Yes | Next scheduled trigger time; `null` when `PAUSED` |
| `recentRuns` | `List<ScheduleRun>` | No | Execution history, newest first, capped at 100 entries |

#### Nested Enums

```java
public enum Cadence { DAILY, WEEKLY, MONTHLY }
public enum Status  { ACTIVE, PAUSED }
```

#### Nested Record: `ScheduleRun`

| Field | Type | Description |
|---|---|---|
| `runId` | `String` | UUID assigned at trigger time |
| `triggeredAt` | `Instant` | When the Timed Action fired |
| `workflowId` | `String` | UUID of the `PodcastCreationWorkflow` instance |
| `workflowStatusUrl` | `String` | Relative URL, e.g. `/podcast/{workflowId}/status` |

`ScheduleRun` is append-only. Once recorded it is never mutated. The list is prepended and trimmed to the 100 most recent entries on each `withRunRecorded` call.

---

## Validation Rules

All validation is enforced in the entity command handler before any state mutation. The domain record's factory and mutation methods assume inputs are already validated.

### Field-level

| Field | Rule |
|---|---|
| `searchTerms` | Must be non-blank (not null, not empty, not whitespace-only) |
| `timeHour` | Integer in range 0–23 inclusive |
| `timeMinute` | Integer in range 0–59 inclusive |
| `dayOfWeek` | Must be non-null when cadence is `WEEKLY`; must be null otherwise |
| `dayOfMonth` | Must be non-null and in range 1–28 when cadence is `MONTHLY`; must be null otherwise |

### Cadence-specific

| Cadence | Required fields | Forbidden fields |
|---|---|---|
| `DAILY` | `timeHour`, `timeMinute` | `dayOfWeek`, `dayOfMonth` |
| `WEEKLY` | `timeHour`, `timeMinute`, `dayOfWeek` | `dayOfMonth` |
| `MONTHLY` | `timeHour`, `timeMinute`, `dayOfMonth` (1–28) | `dayOfWeek` |

The upper bound of 28 for `dayOfMonth` guarantees the schedule fires in every calendar month, including February in non-leap years.

### Business-level

- A user may have at most 10 schedules in any status (`ACTIVE` or `PAUSED`). This limit is enforced at creation time by the endpoint, which queries `SchedulesByOwnerView` before forwarding to the entity.
- Pausing an already-paused schedule is rejected.
- Resuming an already-active schedule is rejected.
- Commands targeting a non-existent entity (null state) return an error, except `RecordRun` which silently returns `Done` in that case.

---

## State Transitions

```
                     CreateSchedule
   [non-existent] ──────────────────► ACTIVE
                                         │
                         PauseSchedule   │   ResumeSchedule
                        ◄────────────────┤────────────────►
                       PAUSED            │                ACTIVE
                        │                │
                DeleteSchedule           │ DeleteSchedule
                        │                │
                        ▼                ▼
                   [non-existent]   [non-existent]
```

### Transition rules

| From | Command | To | Condition |
|---|---|---|---|
| non-existent | `CreateSchedule` | `ACTIVE` | Passes validation |
| `ACTIVE` | `PauseSchedule` | `PAUSED` | Always succeeds |
| `PAUSED` | `ResumeSchedule` | `ACTIVE` | `nextRunAt` recalculated by caller |
| `ACTIVE` or `PAUSED` | `UpdateSchedule` | same status | Allowed in either status |
| `ACTIVE` or `PAUSED` | `DeleteSchedule` | non-existent | Clears KVE state |
| `ACTIVE` | `RecordRun` | `ACTIVE` | Timer appends run, advances `nextRunAt` |
| non-existent | `RecordRun` | non-existent | No-op; returns `Done` |

On `PauseSchedule`, `nextRunAt` is set to `null`. The Timed Action checks status before proceeding; if it finds `PAUSED` it returns `done()` without rescheduling.

On `DeleteSchedule`, the Key Value Entity state is cleared (set to `null`). The outstanding timer (if any) fires once more and then stops because the entity state is null.

---

## Application Components

### `PodcastScheduleEntity`

**Package**: `com.example.application`
**Type**: `KeyValueEntity<PodcastSchedule>`
**Component id**: `"podcast-schedule"`
**Key**: `scheduleId` (UUID)

#### Commands

| Method | Parameter record | Return |
|---|---|---|
| `createSchedule` | `CreateSchedule` | `Effect<ScheduleCreatedResponse>` |
| `pauseSchedule` | none | `Effect<Done>` |
| `resumeSchedule` | `ResumeSchedule(Instant nextRunAt)` | `Effect<Done>` |
| `updateSchedule` | `UpdateSchedule` | `Effect<Done>` |
| `deleteSchedule` | none | `Effect<Done>` |
| `recordRun` | `RecordRun` | `Effect<Done>` |
| `getSchedule` | none | `Effect<PodcastSchedule>` |

`CreateSchedule` inner record:

| Field | Type |
|---|---|
| `userId` | `String` |
| `searchTerms` | `String` |
| `cadence` | `Cadence` |
| `timeHour` | `int` |
| `timeMinute` | `int` |
| `dayOfWeek` | `DayOfWeek` |
| `dayOfMonth` | `Integer` |

`ScheduleCreatedResponse` inner record:

| Field | Type |
|---|---|
| `scheduleId` | `String` |
| `nextRunAt` | `Instant` |

`RecordRun` inner record:

| Field | Type |
|---|---|
| `runId` | `String` |
| `triggeredAt` | `Instant` |
| `workflowId` | `String` |
| `workflowStatusUrl` | `String` |
| `nextRunAt` | `Instant` |

---

### `PodcastScheduleTrigger`

**Package**: `com.example.application`
**Type**: `TimedAction`
**Component id**: `"podcast-schedule-trigger"`

Single method: `trigger(String scheduleId)`

Execution sequence:

1. Fetch schedule state via `componentClient.forKeyValueEntity(scheduleId).method(PodcastScheduleEntity::getSchedule).invoke()`.
2. If state is null or status is `PAUSED`: return `effects().done()`. The timer chain ends here.
3. If status is `ACTIVE`:
   - Generate `runId` (UUID) and `workflowId` (UUID).
   - Start `PodcastCreationWorkflow` instance via `componentClient`.
   - Calculate `nextRunAt = schedule.nextRunAfter(Instant.now())`.
   - Record the run via `componentClient.forKeyValueEntity(scheduleId).method(PodcastScheduleEntity::recordRun).invoke(new RecordRun(...))`.
   - Schedule the next timer: `timerScheduler.createSingleTimer("podcast-schedule-{scheduleId}", delay, deferred call to trigger)`.
   - Return `effects().done()`.

The timer name `"podcast-schedule-{scheduleId}"` is stable. Re-creating a timer with the same name cancels the previous one, which provides natural idempotency when `resume` or `update` reschedules.

---

### `SchedulesByOwnerView`

**Package**: `com.example.application`
**Type**: `View` (consumes `PodcastScheduleEntity`)

#### Row type: `ScheduleSummary`

| Field | Type | Source |
|---|---|---|
| `scheduleId` | `String` | `PodcastSchedule.scheduleId` |
| `userId` | `String` | `PodcastSchedule.userId` |
| `searchTerms` | `String` | `PodcastSchedule.searchTerms` |
| `cadence` | `String` | `PodcastSchedule.cadence.name()` |
| `status` | `String` | `PodcastSchedule.status.name()` |
| `nextRunAt` | `Instant` | `PodcastSchedule.nextRunAt` |
| `createdAt` | `Instant` | `PodcastSchedule.createdAt` |

#### Query

```sql
SELECT * AS schedules
FROM schedule_rows
WHERE userId = :userId
ORDER BY createdAt DESC
```

Response type: `ScheduleSummaries(List<ScheduleSummary> schedules)`

#### Update handler

`onUpdate(PodcastSchedule state)`:
- If `state` is null (entity deleted): `effects().deleteRow()`
- Otherwise: `effects().updateRow(new ScheduleSummary(...))`

---

## API Layer

### `PodcastScheduleEndpoint`

**Package**: `com.example.api`
**Annotation**: `@HttpEndpoint("/schedules")`
**Access control**: `@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))`
**Auth**: `@JWT(validate = JWT.JwtMethodMode.BEARER_TOKEN)`, extends `AbstractHttpEndpoint`
**Injected**: `ComponentClient`, `TimerScheduler`

The authenticated user's `userId` is extracted from the JWT `sub` claim on each request. Ownership checks use this value; users can only access their own schedules.

#### Request / Response records

`CreateScheduleRequest`:

| Field | Type |
|---|---|
| `searchTerms` | `String` |
| `cadence` | `String` |
| `timeHour` | `int` |
| `timeMinute` | `int` |
| `dayOfWeek` | `String` (nullable) |
| `dayOfMonth` | `Integer` (nullable) |

`UpdateScheduleRequest`: same fields as `CreateScheduleRequest` plus `nextRunAt` (derived server-side).

#### Endpoints

| Method | Path | Handler | Success | Error |
|---|---|---|---|---|
| `POST` | `/schedules` | `createSchedule(CreateScheduleRequest)` | 201 `ScheduleCreatedResponse` | 400 validation |
| `GET` | `/schedules` | `listSchedules()` | 200 `ScheduleSummaries` | — |
| `GET` | `/schedules/{id}` | `getSchedule(String id)` | 200 `PodcastSchedule` | 404 |
| `PATCH` | `/schedules/{id}/pause` | `pauseSchedule(String id)` | 200 | 404, 400 |
| `PATCH` | `/schedules/{id}/resume` | `resumeSchedule(String id)` | 200 | 404, 400 |
| `PUT` | `/schedules/{id}` | `updateSchedule(String id, UpdateScheduleRequest)` | 200 | 404, 400 |
| `DELETE` | `/schedules/{id}` | `deleteSchedule(String id)` | 200 | — |
| `GET` | `/schedules/{id}/runs` | `getScheduleRuns(String id)` | 200 `RunsResponse` | 404 |

`createSchedule` also:
1. Queries `SchedulesByOwnerView` to count the user's existing schedules; rejects with 400 if the count is already 10.
2. Generates `scheduleId` (UUID) and calculates the initial `nextRunAt`.
3. Forwards to `PodcastScheduleEntity::createSchedule`.
4. Creates the first timer via `timerScheduler.createSingleTimer(timerName(scheduleId), delay, ...)`.

`pauseSchedule` cancels the pending timer by name using `timerScheduler.cancel(timerName(scheduleId))`.

`resumeSchedule` calculates a new `nextRunAt`, forwards to the entity, then creates a new timer.

`deleteSchedule` cancels the pending timer then forwards to the entity.

`getScheduleRuns` fetches the full `PodcastSchedule` state and returns only its `recentRuns` field wrapped in `RunsResponse(List<ScheduleRun> runs)`.

Timer name helper:

```java
private static String timerName(String scheduleId) {
    return "podcast-schedule-" + scheduleId;
}
```

---

## Component Interaction Diagram

```
HTTP Client
    │
    ▼
PodcastScheduleEndpoint   ──(1. count check)──►  SchedulesByOwnerView
    │                                                      ▲
    │ (2. create / pause / resume / update / delete)       │ (onUpdate)
    ▼                                                      │
PodcastScheduleEntity  ────────────────────────────────────┘
    ▲
    │ (get / recordRun)
    │
PodcastScheduleTrigger ──────────────────────────► PodcastCreationWorkflow
    ▲                         (start new run)
    │
TimerScheduler
(fires at nextRunAt)
```

Sequence for a scheduled trigger:

```
TimerScheduler
    │  fires
    ▼
PodcastScheduleTrigger.trigger(scheduleId)
    │
    ├──► PodcastScheduleEntity.getSchedule()          [check status]
    │
    ├──► PodcastCreationWorkflow (start, workflowId)  [begin pipeline]
    │
    ├──► PodcastScheduleEntity.recordRun(...)          [append run, advance nextRunAt]
    │
    └──► TimerScheduler.createSingleTimer(...)         [schedule next tick]
```

---

## Constraints and Limits

| Constraint | Value | Enforcement point |
|---|---|---|
| Max schedules per user | 10 (active + paused) | `PodcastScheduleEndpoint.createSchedule` |
| Max stored runs per schedule | 100 (newest first) | `PodcastSchedule.withRunRecorded` |
| Execution history retention | 90 days | Out of scope for this feature; future cleanup job |
| Min `dayOfMonth` | 1 | Entity validation |
| Max `dayOfMonth` | 28 | Entity validation (ensures every calendar month fires) |
| Schedule time precision | Minute | Timer resolution; sub-minute not supported |
| All times | UTC | No per-user or per-schedule timezone |

---

## Key Design Decisions

**Why Key Value Entity (not Event Sourced)?**
Schedule configuration changes are driven by user intent, not business events worth auditing. The execution history (the part worth retaining) is stored as an embedded list within the aggregate and bounded to 100 entries. Event sourcing would add unbounded event log growth with no benefit here.

**Why inline run history (not a separate entity)?**
The bounded nature (100 runs) and the access pattern (always fetched alongside the schedule) make embedding appropriate. A separate entity would add a join and a second round-trip with no gain at this scale.

**Why `dayOfMonth` capped at 28?**
Values 29–31 would cause skipped runs in short months. Capping at 28 gives a simple, predictable guarantee without requiring month-aware skip logic.

**Why timer-per-schedule (single timer chain)?**
Each schedule maintains one outstanding `TimerScheduler` timer. The trigger action re-schedules the next timer after each run. Cancelling the timer by its stable name (`podcast-schedule-{scheduleId}`) is a reliable way to stop a paused or deleted schedule without needing a separate scheduling registry.

**Why no catch-up on missed runs?**
The spec explicitly states no catch-up (FR-016). If the system was unavailable at trigger time, the run is skipped and the timer chain resumes at the next natural period.
