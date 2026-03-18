# Feature Specification: Scheduled Podcast Creation

**Feature Branch**: `003-podcast-schedule`
**Created**: 2026-03-18
**Status**: Draft
**Input**: User description: "allow users to configure search terms and a cadence (daily, weekly or monthly - which time of day, day of week or month) and then trigger the search, scripting and creation of podcast on that cadence"

## Overview

Authenticated users can create recurring podcast schedules that automatically trigger the full podcast creation pipeline (research → scripting → audio) at a configured cadence. This removes the need for users to manually initiate each podcast run.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Create and Activate a Podcast Schedule (Priority: P1)

A user wants the system to automatically produce a new podcast on a recurring basis without manual effort. They configure a schedule with the search topic they care about and how often they want a new episode produced.

**Why this priority**: This is the core value of the feature. Everything else depends on being able to create a working schedule.

**Independent Test**: A user creates a schedule (e.g., "machine learning" weekly on Mondays at 09:00 UTC). At the next scheduled time, the system automatically produces a podcast. The user never had to press a button.

**Acceptance Scenarios**:

1. **Given** an authenticated user, **When** they create a daily schedule with search terms and a time of day, **Then** the schedule is saved and active, and a podcast is automatically created at that time each day.
2. **Given** an authenticated user, **When** they create a weekly schedule specifying a day of week and time, **Then** a podcast is produced at that time on that day each week.
3. **Given** an authenticated user, **When** they create a monthly schedule specifying a day of month and time, **Then** a podcast is produced at that time on that day each month.
4. **Given** a user attempts to create a schedule with blank search terms, **When** they submit, **Then** the request is rejected with a clear error.
5. **Given** a user already has 10 active schedules, **When** they attempt to create another, **Then** the request is rejected with a message indicating the limit has been reached.

---

### User Story 2 — Manage Existing Schedules (Priority: P2)

A user wants to update, pause, resume, or remove their podcast schedules as their interests or availability changes.

**Why this priority**: Schedules become useless without lifecycle management. Without pause/resume, users must delete and recreate schedules.

**Independent Test**: A user creates a schedule, then pauses it. No podcast is created at the next scheduled time. The user resumes it and the next scheduled time produces a podcast again.

**Acceptance Scenarios**:

1. **Given** an active schedule, **When** the user pauses it, **Then** no podcast is created at the next scheduled time.
2. **Given** a paused schedule, **When** the user resumes it, **Then** podcasts are created again at the next scheduled time.
3. **Given** an existing schedule, **When** the user updates the search terms or cadence, **Then** future runs use the new configuration.
4. **Given** an existing schedule, **When** the user deletes it, **Then** the schedule no longer appears in their list and no further runs occur.
5. **Given** a user with multiple schedules, **When** they list their schedules, **Then** all schedules are returned with their current status (active/paused) and next scheduled run time.

---

### User Story 3 — View Schedule Execution History (Priority: P3)

A user wants to know whether their scheduled runs have succeeded or failed, and be able to navigate to the produced podcast when a run completes successfully.

**Why this priority**: Valuable for debugging and production tracking, but the core scheduling value is delivered without it.

**Independent Test**: After a scheduled run completes, the user can retrieve the execution history for that schedule and see the outcome, timestamp, and a link to the resulting podcast.

**Acceptance Scenarios**:

1. **Given** a schedule that has run at least once, **When** the user requests its execution history, **Then** they see one entry per run with a timestamp and status (running / complete / failed).
2. **Given** a completed run, **When** the user views its history entry, **Then** they can navigate to the produced podcast (status URL).
3. **Given** a failed run, **When** the user views its history entry, **Then** they see a failure indicator with no podcast link.

---

### Edge Cases

- What if a scheduled run is still in progress when the next period arrives? The in-progress run continues; the new run is skipped for that period.
- What if the system was unavailable at a scheduled time? The run is skipped; no catch-up execution occurs after recovery.
- What if a monthly schedule has day-of-month set higher than a given month's length (e.g., day 31 in February)? The run is skipped for that month.
- What if two users schedule the same search terms at the same time? Each produces an independent podcast; there is no cross-user deduplication.
- What if a user deletes their account? All their schedules are cancelled and no further runs occur.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Authenticated users MUST be able to create a podcast schedule specifying: non-blank search terms, cadence (DAILY / WEEKLY / MONTHLY), and the appropriate time configuration for that cadence.
- **FR-002**: For DAILY schedules, time configuration MUST be a time of day (hour and minute) interpreted as UTC. All schedule times across cadences are UTC; no per-schedule or per-user timezone is supported.
- **FR-003**: For WEEKLY schedules, time configuration MUST include a day of week (Monday–Sunday) and a time of day.
- **FR-004**: For MONTHLY schedules, time configuration MUST include a day of month (1–28) and a time of day. Day values 29–31 are not accepted at creation time to guarantee execution in every calendar month.
- **FR-005**: The system MUST automatically trigger the full podcast creation pipeline at each scheduled time for active schedules.
- **FR-006**: Authenticated users MUST be able to list all their schedules, each showing current status (ACTIVE / PAUSED) and next scheduled run time.
- **FR-007**: Authenticated users MUST be able to pause an active schedule, stopping automatic runs without deleting it.
- **FR-008**: Authenticated users MUST be able to resume a paused schedule, re-enabling runs from the next scheduled period.
- **FR-009**: Authenticated users MUST be able to update a schedule's search terms, cadence, or time configuration.
- **FR-010**: Authenticated users MUST be able to delete a schedule permanently.
- **FR-011**: The system MUST record each scheduled execution attempt with its start time, completion time, and outcome (RUNNING / COMPLETE / FAILED).
- **FR-012**: Authenticated users MUST be able to retrieve the execution history for a specific schedule.
- **FR-013**: Successful execution history entries MUST include a navigable reference to the produced podcast.
- **FR-014**: Each user MUST be limited to a maximum of 10 schedules (active or paused) at any time.
- **FR-015**: If a scheduled run is already in progress when the next period arrives, the incoming run MUST be skipped for that period.
- **FR-016**: Schedules that are paused, deleted, or whose time was missed due to system unavailability MUST NOT trigger catch-up runs.

### Key Entities *(include if feature involves data)*

- **PodcastSchedule**: A user's recurring podcast configuration. Key attributes: schedule ID, owner user ID, search terms, cadence (DAILY/WEEKLY/MONTHLY), time of day (hour, minute), day of week (WEEKLY only), day of month (MONTHLY only, 1–28), status (ACTIVE/PAUSED), created date, last modified date, next scheduled run time.
- **ScheduleExecution**: A single triggered run of a schedule. Key attributes: execution ID, schedule ID, triggered-at timestamp, completed-at timestamp, status (RUNNING/COMPLETE/FAILED), podcast reference (populated on successful completion).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can create a new podcast schedule in under 2 minutes from start to confirmation.
- **SC-002**: Scheduled podcast runs fire within 5 minutes of their configured time under normal operating conditions.
- **SC-003**: 100% of active, non-conflicting schedules produce a run attempt at each scheduled period — no silent misses.
- **SC-004**: Users can view all their schedules and the outcome of each schedule's last run on a single request.
- **SC-005**: Pausing a schedule guarantees no further automatic runs without any manual follow-up required.
- **SC-006**: The system reliably handles at least 1,000 concurrent scheduled users without degradation in scheduling accuracy.

## Clarifications

### Session 2026-03-18

- Q: Should schedule times be UTC or user-specified timezone? → A: UTC only — all schedule times are interpreted as UTC; no per-schedule or account-level timezone support.

## Assumptions

- Users are authenticated via the existing Auth0 JWT mechanism; feature 002 (User Accounts) is a prerequisite.
- The existing podcast creation pipeline (feature 001) is invoked as-is; this feature only automates its triggering.
- Schedule times are exact to the minute; sub-minute precision is not required.
- The day-of-month cap of 28 ensures execution in every calendar month, including February.
- A user may have multiple schedules for the same search terms (e.g., both a daily and a weekly schedule for "AI news").
- Schedules are personal and not shareable between users.
- Execution history is retained for 90 days then automatically removed.
- This feature does not include notifications (push, email, SMS) when runs complete; users check status via execution history.
- Search terms in schedules are independent of saved queries from feature 002; they are stored inline in the schedule.
