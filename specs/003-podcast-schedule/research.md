# Research: Podcast Schedule Feature (003)

Authenticated users configure recurring podcast schedules (DAILY/WEEKLY/MONTHLY with UTC times). At each scheduled time the system automatically triggers the existing `PodcastCreationWorkflow` (feature 001).

---

## Decision 1: Scheduling Mechanism

**Decision:** Use Akka `TimerScheduler` injected into a `TimedAction` component.

The `TimedAction` receives a `scheduleId`, checks the current entity state, fires the `PodcastCreationWorkflow`, records the run in the KVE state, and reschedules the next timer. The endpoint creates the first timer on schedule creation and cancels it on pause or delete.

**Rationale:** Akka-native approach with persistence guarantees — timers survive service restarts and are backed by the Akka cluster. `TimerScheduler` is injectable into Endpoints, Consumers, Timed Actions, and Workflows. The self-rescheduling pattern (each `TimedAction` invocation schedules its own successor) avoids long-running state and maps naturally to recurring cadences.

**Alternatives considered:**
- Cron job — non-Akka, not portable, requires external infrastructure.
- `ScheduledExecutorService` — non-durable, timers lost on restart.
- Workflow with `sleep` step — feasible but expensive for long cadences (WEEKLY/MONTHLY); workflow state would be held open for days or weeks.

---

## Decision 2: Schedule State Storage

**Decision:** `KeyValueEntity<PodcastSchedule>` keyed by UUID `scheduleId`.

**Rationale:** A schedule is a mutable configuration object (search terms, cadence, status, next run time). There is no requirement to replay or audit the history of configuration changes. KVE is the correct fit: simpler API, lower storage overhead, and direct state updates without event projection.

**Alternatives considered:**
- Event Sourced Entity — correct tool for entities requiring an audit trail or event-driven projections. Overkill here; no business requirement for change history on schedule config.

---

## Decision 3: Execution History Storage

**Decision:** Store execution history as a bounded list (max 100 entries) inside the `PodcastSchedule` KVE state. Each entry stores `runId`, `triggeredAt`, `workflowId`, and `workflowStatusUrl`.

**Rationale:** Keeps the data model simple and co-located. 100 entries is sufficient for operational visibility. The bounded list prevents unbounded growth. Because execution records are written by the same `TimedAction` that updates the entity, consistency is maintained without cross-component coordination.

**Alternatives considered:**
- Separate execution entities with a dedicated View — cleaner separation of concerns but introduces significant added complexity (additional entity type, additional view, cross-entity references) for what is a P3 feature. Deferred to a future iteration if audit depth becomes a requirement.

---

## Decision 4: Execution Outcome Tracking

**Decision:** Execution records store only `triggeredAt` and `workflowId`. Actual outcome (RUNNING/COMPLETE/FAILED) is derived on demand by following `workflowStatusUrl`, which resolves to the existing `/podcast/{id}/status` endpoint exposed by `PodcastCreationWorkflow` (feature 001).

**Rationale:** Avoids the complexity of a `Consumer` that listens to every workflow state transition and backfills the schedule history. Outcome data is available from the workflow itself; there is no need to duplicate it into the schedule entity at this stage.

**Alternatives considered:**
- `Consumer` from `PodcastCreationWorkflow` backfilling completion status into the schedule entity on every workflow update — architecturally correct but introduces cross-feature coupling and additional consumer complexity. Deferred to post-MVP.

---

## Decision 5: Schedule Listing

**Decision:** `SchedulesByOwnerView` (View from KVE) with query:

```sql
SELECT * AS schedules FROM schedule_rows WHERE userId = :userId
```

The View's `onUpdate(PodcastSchedule)` method maintains one row per schedule. The endpoint passes the authenticated `userId` as the query parameter.

**Rationale:** Standard Akka View pattern for KVE-backed projections. `onUpdate` is the correct handler for KVE views (not `onEvent`). The `userId` field on `PodcastSchedule` state is indexed by the View row, enabling efficient per-user queries.

**Alternatives considered:**
- Consumer-based projection — produces the same result but requires an additional `Consumer` component and a separate topic or stream. More moving parts for equivalent functionality.

---

## Decision 6: Next-Run Calculation

**Decision:** Computed in the domain record `PodcastSchedule.nextRunAfter(Instant now)` using `java.time` (`ZonedDateTime` in UTC).

- **DAILY:** next occurrence of `HH:MM` UTC after `now`.
- **WEEKLY:** next occurrence of `dayOfWeek` + `HH:MM` UTC after `now`.
- **MONTHLY:** next occurrence of `dayOfMonth` + `HH:MM` UTC after `now`; months where the day does not exist (e.g., day 31 in February) are skipped to the following eligible month.

The domain method is pure and testable without any Akka dependencies.

**Rationale:** Recurrence logic belongs in the domain layer. Computing next-run from the current time at each trigger (rather than from a stored absolute timestamp) prevents drift — if a trigger fires slightly late, the next interval is still computed from `now`, not from the originally scheduled time.

**Alternatives considered:**
- Store next run as an absolute timestamp computed once at creation, then advance by a fixed interval at each trigger — prone to cumulative drift over many cycles.

---

## Decision 7: Timer Naming

**Decision:** Timer names follow the pattern `podcast-schedule-{scheduleId}`.

**Rationale:** Timer names are unique cluster-wide in Akka. Using the UUID-based `scheduleId` in the timer name guarantees at-most-one active timer per schedule. Creating a new timer with the same name replaces any existing timer, so rescheduling is idempotent.

---

## Decision 8: Entity Key Encoding

**Decision:** The entity key is the UUID `scheduleId`. No special encoding is needed.

**Rationale:** Auth0 user IDs contain `|` characters, which can be problematic in URL path segments and Akka entity keys. By using a UUID as the entity key rather than the `userId`, encoding issues are avoided entirely. The `userId` is stored as a plain field in the KVE state and used as a View query parameter (passed via query string, not path).

---

## Decision 9: Schedule Limit Enforcement

**Decision:** Enforce a maximum of 10 active schedules per user in the **endpoint**, not in the entity. The endpoint queries `SchedulesByOwnerView` for the current count before calling `createSchedule` on the entity. If the count is >= 10, the endpoint returns a 400 error without touching the entity.

**Rationale:** An entity cannot call a View in Akka — component client access is restricted to Endpoints, Agents, Consumers, Timed Actions, and Workflows. The endpoint is the correct place to enforce this cross-entity constraint. Race conditions (two concurrent creates both reading a count of 9) are acceptable at this scale; the limit is a soft guardrail, not a hard invariant.

**Alternatives considered:**
- Store schedule count in `UserAccountEntity` (feature 002) and enforce there — rejected because it creates cross-feature coupling between the user account and schedule domains. Changes to either entity would require coordination.
