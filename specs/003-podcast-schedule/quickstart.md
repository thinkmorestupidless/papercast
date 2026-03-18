# Quickstart: Podcast Schedule API

Base URL: `http://localhost:9000`
All requests require: `Authorization: Bearer $TOKEN`

---

## Scenario 1: Create and verify a weekly schedule

Create a weekly schedule for "machine learning" that fires every Monday at 09:00 UTC, then confirm it appears in the schedule list and can be fetched by ID.

### Step 1 — Create the schedule

```bash
curl -s -X POST http://localhost:9000/schedules \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "searchTerms": "machine learning",
    "cadence": "WEEKLY",
    "timeHour": 9,
    "timeMinute": 0,
    "dayOfWeek": "MONDAY",
    "dayOfMonth": null
  }'
```

Expected response — `201 Created`:

```json
{
  "scheduleId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "nextRunAt": "2026-03-23T09:00:00Z"
}
```

Note the `scheduleId` and `nextRunAt`. The next Monday at 09:00 UTC is when the first podcast pipeline will be triggered automatically.

### Step 2 — List all schedules

```bash
curl -s http://localhost:9000/schedules \
  -H "Authorization: Bearer $TOKEN"
```

Expected response — `200 OK`:

```json
{
  "schedules": [
    {
      "scheduleId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "userId": "auth0|user123",
      "searchTerms": "machine learning",
      "cadence": "WEEKLY",
      "status": "ACTIVE",
      "nextRunAt": "2026-03-23T09:00:00Z",
      "createdAt": "2026-03-18T14:00:00Z"
    }
  ]
}
```

Verify: the schedule appears with `"status": "ACTIVE"` and a non-null `nextRunAt`.

### Step 3 — Fetch the full schedule details

```bash
curl -s http://localhost:9000/schedules/a1b2c3d4-e5f6-7890-abcd-ef1234567890 \
  -H "Authorization: Bearer $TOKEN"
```

Expected response — `200 OK`:

```json
{
  "scheduleId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "userId": "auth0|user123",
  "searchTerms": "machine learning",
  "cadence": "WEEKLY",
  "timeHour": 9,
  "timeMinute": 0,
  "dayOfWeek": "MONDAY",
  "dayOfMonth": null,
  "status": "ACTIVE",
  "createdAt": "2026-03-18T14:00:00Z",
  "lastModifiedAt": "2026-03-18T14:00:00Z",
  "nextRunAt": "2026-03-23T09:00:00Z",
  "recentRuns": []
}
```

Verify: `cadence`, `dayOfWeek`, `timeHour`, and `timeMinute` all match what was submitted.

---

## Scenario 2: Pause, resume, and confirm behavior

Create a daily schedule, pause it, confirm `nextRunAt` clears, resume it, and confirm `nextRunAt` is recalculated.

### Step 1 — Create a daily schedule

```bash
curl -s -X POST http://localhost:9000/schedules \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "searchTerms": "artificial intelligence news",
    "cadence": "DAILY",
    "timeHour": 7,
    "timeMinute": 30,
    "dayOfWeek": null,
    "dayOfMonth": null
  }'
```

Expected response — `201 Created`:

```json
{
  "scheduleId": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
  "nextRunAt": "2026-03-19T07:30:00Z"
}
```

### Step 2 — Pause the schedule

```bash
curl -s -X PATCH \
  http://localhost:9000/schedules/b2c3d4e5-f6a7-8901-bcde-f12345678901/pause \
  -H "Authorization: Bearer $TOKEN"
```

Expected response — `200 OK` (empty body or `{}`).

### Step 3 — Confirm the schedule is paused

```bash
curl -s http://localhost:9000/schedules/b2c3d4e5-f6a7-8901-bcde-f12345678901 \
  -H "Authorization: Bearer $TOKEN"
```

Expected response — `200 OK`:

```json
{
  "scheduleId": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
  "status": "PAUSED",
  "nextRunAt": null,
  ...
}
```

Verify: `"status": "PAUSED"` and `"nextRunAt": null`. The pending timer has been cancelled; no podcast will be triggered until the schedule is resumed.

### Step 4 — Resume the schedule

```bash
curl -s -X PATCH \
  http://localhost:9000/schedules/b2c3d4e5-f6a7-8901-bcde-f12345678901/resume \
  -H "Authorization: Bearer $TOKEN"
```

Expected response — `200 OK` (empty body or `{}`). The server recalculates `nextRunAt` from the current time and creates a new timer.

### Step 5 — Confirm the schedule is active again

```bash
curl -s http://localhost:9000/schedules/b2c3d4e5-f6a7-8901-bcde-f12345678901 \
  -H "Authorization: Bearer $TOKEN"
```

Expected response — `200 OK`:

```json
{
  "scheduleId": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
  "status": "ACTIVE",
  "nextRunAt": "2026-03-19T07:30:00Z",
  ...
}
```

Verify: `"status": "ACTIVE"` and `nextRunAt` is a future timestamp.

---

## Scenario 3: Update and delete

Create a monthly schedule, update it to a different cadence, verify the change, then delete it and confirm it is gone.

### Step 1 — Create a monthly schedule

```bash
curl -s -X POST http://localhost:9000/schedules \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "searchTerms": "deep learning research",
    "cadence": "MONTHLY",
    "timeHour": 8,
    "timeMinute": 0,
    "dayOfWeek": null,
    "dayOfMonth": 1
  }'
```

Expected response — `201 Created`:

```json
{
  "scheduleId": "c3d4e5f6-a7b8-9012-cdef-123456789012",
  "nextRunAt": "2026-04-01T08:00:00Z"
}
```

### Step 2 — Update to a daily schedule at 07:00 UTC

```bash
curl -s -X PUT \
  http://localhost:9000/schedules/c3d4e5f6-a7b8-9012-cdef-123456789012 \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "searchTerms": "deep learning research",
    "cadence": "DAILY",
    "timeHour": 7,
    "timeMinute": 0,
    "dayOfWeek": null,
    "dayOfMonth": null
  }'
```

Expected response — `200 OK`.

### Step 3 — Verify the new config and recalculated nextRunAt

```bash
curl -s http://localhost:9000/schedules/c3d4e5f6-a7b8-9012-cdef-123456789012 \
  -H "Authorization: Bearer $TOKEN"
```

Expected response — `200 OK`:

```json
{
  "scheduleId": "c3d4e5f6-a7b8-9012-cdef-123456789012",
  "searchTerms": "deep learning research",
  "cadence": "DAILY",
  "timeHour": 7,
  "timeMinute": 0,
  "dayOfWeek": null,
  "dayOfMonth": null,
  "status": "ACTIVE",
  "nextRunAt": "2026-03-19T07:00:00Z",
  ...
}
```

Verify: `"cadence": "DAILY"`, `"timeHour": 7`, and `nextRunAt` reflects the next 07:00 UTC occurrence (not the old monthly value).

### Step 4 — Delete the schedule

```bash
curl -s -X DELETE \
  http://localhost:9000/schedules/c3d4e5f6-a7b8-9012-cdef-123456789012 \
  -H "Authorization: Bearer $TOKEN"
```

Expected response — `200 OK`. The pending timer is also cancelled server-side.

### Step 5 — Confirm 404 after deletion

```bash
curl -s -o /dev/null -w "%{http_code}" \
  http://localhost:9000/schedules/c3d4e5f6-a7b8-9012-cdef-123456789012 \
  -H "Authorization: Bearer $TOKEN"
```

Expected output: `404`

---

## Scenario 4: View execution history

Create a schedule, wait for (or manually trigger) a run, then inspect the execution history and follow the workflow status link.

### Step 1 — Create a schedule and note the scheduleId

```bash
curl -s -X POST http://localhost:9000/schedules \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "searchTerms": "neural networks",
    "cadence": "DAILY",
    "timeHour": 6,
    "timeMinute": 0,
    "dayOfWeek": null,
    "dayOfMonth": null
  }'
```

Expected response — `201 Created`:

```json
{
  "scheduleId": "d4e5f6a7-b8c9-0123-defa-234567890123",
  "nextRunAt": "2026-03-19T06:00:00Z"
}
```

Note the `scheduleId`: `d4e5f6a7-b8c9-0123-defa-234567890123`.

### Step 2 — Trigger a run (test shortcut)

In a real deployment, the run fires automatically at `nextRunAt`. For local testing, call the `PodcastScheduleTrigger` TimedAction directly:

```bash
curl -s -X POST \
  "http://localhost:9000/akka/components/podcast-schedule-trigger/d4e5f6a7-b8c9-0123-defa-234567890123/trigger" \
  -H "Authorization: Bearer $TOKEN"
```

Alternatively, wait until the scheduled time passes and the timer fires automatically.

### Step 3 — Retrieve the execution history

```bash
curl -s \
  http://localhost:9000/schedules/d4e5f6a7-b8c9-0123-defa-234567890123/runs \
  -H "Authorization: Bearer $TOKEN"
```

Expected response — `200 OK`:

```json
{
  "runs": [
    {
      "runId": "e5f6a7b8-c9d0-1234-efab-345678901234",
      "triggeredAt": "2026-03-19T06:00:03Z",
      "workflowId": "f6a7b8c9-d0e1-2345-fabc-456789012345",
      "workflowStatusUrl": "/podcast/f6a7b8c9-d0e1-2345-fabc-456789012345/status"
    }
  ]
}
```

Verify: at least one entry appears with `triggeredAt`, `workflowId`, and `workflowStatusUrl` populated.

### Step 4 — Follow the workflowStatusUrl to check podcast creation

Use the `workflowStatusUrl` from the run entry to check the podcast creation progress:

```bash
curl -s \
  http://localhost:9000/podcast/f6a7b8c9-d0e1-2345-fabc-456789012345/status \
  -H "Authorization: Bearer $TOKEN"
```

Expected response — `200 OK` (response shape comes from `PodcastCreationWorkflow`, feature 001):

```json
{
  "workflowId": "f6a7b8c9-d0e1-2345-fabc-456789012345",
  "status": "RUNNING",
  "currentStep": "SCRIPTING",
  "startedAt": "2026-03-19T06:00:03Z",
  "completedAt": null,
  "podcastUrl": null
}
```

Once the workflow completes, `"status"` changes to `"COMPLETE"` and `"podcastUrl"` is populated with a link to the produced audio.

Poll for completion:

```bash
watch -n 5 'curl -s \
  http://localhost:9000/podcast/f6a7b8c9-d0e1-2345-fabc-456789012345/status \
  -H "Authorization: Bearer $TOKEN" | jq .status'
```

---

## Common errors

| Scenario | Status | Cause |
|---|---|---|
| Create with blank `searchTerms` | `400` | Validation: `searchTerms` must be non-blank |
| Create WEEKLY without `dayOfWeek` | `400` | Validation: `dayOfWeek` required for WEEKLY cadence |
| Create MONTHLY with `dayOfMonth` > 28 | `400` | Validation: day capped at 28 to guarantee monthly execution |
| Create when 10 schedules already exist | `400` | Per-user schedule limit reached |
| Pause an already-paused schedule | `400` | Invalid state transition |
| Resume an already-active schedule | `400` | Invalid state transition |
| GET/PATCH/PUT/DELETE on unknown `scheduleId` | `404` | Schedule does not exist |
| Any request without `Authorization` header | `401` | JWT bearer token required |
