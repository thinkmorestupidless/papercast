# HTTP API Contract: Podcast Schedule

## Overview

Base path: `/schedules`

All endpoints require Bearer JWT authentication issued by Auth0. The service is an Akka SDK Java 21 web service.

---

## Authentication

Every request must include a valid JWT in the `Authorization` header:

```
Authorization: Bearer <token>
```

### JWT Validation Rules

- If the `Authorization` header is missing or the token is malformed, the service returns `400` or `401`.
- If the JWT is valid but the `email_verified` claim is `false`, the service returns `403 Forbidden`.

---

## Error Response Format

All error responses use the following JSON structure:

```json
{ "error": "Human-readable error message" }
```

---

## Endpoints

### POST /schedules

Creates a new podcast schedule for the authenticated user.

#### Request

**Headers:**
```
Authorization: Bearer <token>
Content-Type: application/json
```

**Body:**
```json
{
  "searchTerms": "machine learning papers",
  "cadence": "WEEKLY",
  "timeHour": 9,
  "timeMinute": 0,
  "dayOfWeek": "MONDAY",
  "dayOfMonth": null
}
```

**Field Constraints:**

| Field | Type | Required | Constraints |
|---|---|---|---|
| `searchTerms` | string | Yes | Non-blank |
| `cadence` | string | Yes | One of: `DAILY`, `WEEKLY`, `MONTHLY` |
| `timeHour` | integer | Yes | 0–23 (UTC) |
| `timeMinute` | integer | Yes | 0–59 |
| `dayOfWeek` | string or null | Conditional | Required when `cadence` is `WEEKLY`; must be one of `MONDAY`–`SUNDAY`; null otherwise |
| `dayOfMonth` | integer or null | Conditional | Required when `cadence` is `MONTHLY`; must be 1–28; null otherwise |

#### Response

**201 Created:**
```json
{
  "scheduleId": "uuid",
  "nextRunAt": "2026-03-24T09:00:00Z"
}
```

**Error Responses:**

| Status | Condition |
|---|---|
| `400 Bad Request` | `searchTerms` is blank, `cadence` is invalid, `timeHour` or `timeMinute` are out of range, `dayOfWeek`/`dayOfMonth` constraints violated |
| `403 Forbidden` | JWT `email_verified` claim is `false` |
| `429 Too Many Requests` | Authenticated user already has 10 schedules |

---

### GET /schedules

Lists all schedules belonging to the authenticated user.

#### Request

**Headers:**
```
Authorization: Bearer <token>
```

No request body.

#### Response

**200 OK:**
```json
{
  "schedules": [
    {
      "scheduleId": "uuid",
      "searchTerms": "machine learning papers",
      "cadence": "WEEKLY",
      "status": "ACTIVE",
      "nextRunAt": "2026-03-24T09:00:00Z",
      "createdAt": "2026-03-18T10:00:00Z"
    }
  ]
}
```

**Field Descriptions:**

| Field | Type | Description |
|---|---|---|
| `scheduleId` | string (UUID) | Unique identifier for the schedule |
| `searchTerms` | string | The search query used when the schedule runs |
| `cadence` | string | One of: `DAILY`, `WEEKLY`, `MONTHLY` |
| `status` | string | One of: `ACTIVE`, `PAUSED` |
| `nextRunAt` | string (ISO 8601) | UTC timestamp of the next scheduled execution; null if paused |
| `createdAt` | string (ISO 8601) | UTC timestamp when the schedule was created |

Returns an empty `schedules` array if the user has no schedules.

---

### GET /schedules/{scheduleId}

Gets full details for a single schedule.

#### Request

**Path Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `scheduleId` | string (UUID) | ID of the schedule to retrieve |

**Headers:**
```
Authorization: Bearer <token>
```

No request body.

#### Response

**200 OK:**
```json
{
  "scheduleId": "uuid",
  "userId": "auth0|abc123",
  "searchTerms": "machine learning papers",
  "cadence": "WEEKLY",
  "timeHour": 9,
  "timeMinute": 0,
  "dayOfWeek": "MONDAY",
  "dayOfMonth": null,
  "status": "ACTIVE",
  "createdAt": "2026-03-18T10:00:00Z",
  "lastModifiedAt": "2026-03-18T10:00:00Z",
  "nextRunAt": "2026-03-24T09:00:00Z"
}
```

**Field Descriptions:**

| Field | Type | Description |
|---|---|---|
| `scheduleId` | string (UUID) | Unique identifier for the schedule |
| `userId` | string | Auth0 user ID of the schedule owner |
| `searchTerms` | string | The search query used when the schedule runs |
| `cadence` | string | One of: `DAILY`, `WEEKLY`, `MONTHLY` |
| `timeHour` | integer | Hour of day in UTC (0–23) |
| `timeMinute` | integer | Minute of hour (0–59) |
| `dayOfWeek` | string or null | Day of week for `WEEKLY` schedules; null otherwise |
| `dayOfMonth` | integer or null | Day of month for `MONTHLY` schedules; null otherwise |
| `status` | string | One of: `ACTIVE`, `PAUSED` |
| `createdAt` | string (ISO 8601) | UTC timestamp when the schedule was created |
| `lastModifiedAt` | string (ISO 8601) | UTC timestamp of the most recent modification |
| `nextRunAt` | string (ISO 8601) | UTC timestamp of the next scheduled execution; null if paused |

**Error Responses:**

| Status | Condition |
|---|---|
| `404 Not Found` | Schedule does not exist, or exists but belongs to a different user |

---

### PATCH /schedules/{scheduleId}/pause

Pauses an active schedule and cancels its pending timer.

#### Request

**Path Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `scheduleId` | string (UUID) | ID of the schedule to pause |

**Headers:**
```
Authorization: Bearer <token>
```

No request body.

#### Response

**200 OK:**
```json
{}
```

**Error Responses:**

| Status | Condition |
|---|---|
| `400 Bad Request` | Schedule is already paused |
| `404 Not Found` | Schedule does not exist, or exists but belongs to a different user |

---

### PATCH /schedules/{scheduleId}/resume

Resumes a paused schedule and reschedules the timer.

#### Request

**Path Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `scheduleId` | string (UUID) | ID of the schedule to resume |

**Headers:**
```
Authorization: Bearer <token>
```

No request body.

#### Response

**200 OK:**
```json
{ "nextRunAt": "2026-03-24T09:00:00Z" }
```

**Field Descriptions:**

| Field | Type | Description |
|---|---|---|
| `nextRunAt` | string (ISO 8601) | UTC timestamp of the next scheduled execution after resuming |

**Error Responses:**

| Status | Condition |
|---|---|
| `400 Bad Request` | Schedule is already active |
| `404 Not Found` | Schedule does not exist, or exists but belongs to a different user |

---

### PUT /schedules/{scheduleId}

Updates a schedule's full configuration. Cancels the existing timer and reschedules with the new configuration.

#### Request

**Path Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `scheduleId` | string (UUID) | ID of the schedule to update |

**Headers:**
```
Authorization: Bearer <token>
Content-Type: application/json
```

**Body** (all fields required):
```json
{
  "searchTerms": "deep learning papers",
  "cadence": "DAILY",
  "timeHour": 8,
  "timeMinute": 30,
  "dayOfWeek": null,
  "dayOfMonth": null
}
```

**Field Constraints:**

| Field | Type | Required | Constraints |
|---|---|---|---|
| `searchTerms` | string | Yes | Non-blank |
| `cadence` | string | Yes | One of: `DAILY`, `WEEKLY`, `MONTHLY` |
| `timeHour` | integer | Yes | 0–23 (UTC) |
| `timeMinute` | integer | Yes | 0–59 |
| `dayOfWeek` | string or null | Conditional | Required when `cadence` is `WEEKLY`; must be one of `MONDAY`–`SUNDAY`; null otherwise |
| `dayOfMonth` | integer or null | Conditional | Required when `cadence` is `MONTHLY`; must be 1–28; null otherwise |

#### Response

**200 OK:**
```json
{ "nextRunAt": "2026-03-19T08:30:00Z" }
```

**Field Descriptions:**

| Field | Type | Description |
|---|---|---|
| `nextRunAt` | string (ISO 8601) | UTC timestamp of the next scheduled execution under the new configuration |

**Error Responses:**

| Status | Condition |
|---|---|
| `400 Bad Request` | Any field fails validation (same rules as POST) |
| `404 Not Found` | Schedule does not exist, or exists but belongs to a different user |

---

### DELETE /schedules/{scheduleId}

Permanently deletes a schedule and cancels its timer. This action is irreversible.

#### Request

**Path Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `scheduleId` | string (UUID) | ID of the schedule to delete |

**Headers:**
```
Authorization: Bearer <token>
```

No request body.

#### Response

**200 OK:**
```json
{}
```

**Error Responses:**

| Status | Condition |
|---|---|
| `404 Not Found` | Schedule does not exist, or exists but belongs to a different user |

---

### GET /schedules/{scheduleId}/runs

Gets the execution history for a schedule. Results are returned most-recent-first, capped at 100 entries.

#### Request

**Path Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `scheduleId` | string (UUID) | ID of the schedule whose run history to retrieve |

**Headers:**
```
Authorization: Bearer <token>
```

No request body.

#### Response

**200 OK:**
```json
{
  "runs": [
    {
      "runId": "uuid",
      "triggeredAt": "2026-03-24T09:00:01Z",
      "workflowId": "uuid",
      "workflowStatusUrl": "/podcast/uuid/status"
    }
  ]
}
```

**Field Descriptions:**

| Field | Type | Description |
|---|---|---|
| `runId` | string (UUID) | Unique identifier for this execution run |
| `triggeredAt` | string (ISO 8601) | UTC timestamp when this run was triggered |
| `workflowId` | string (UUID) | ID of the podcast workflow that was started for this run |
| `workflowStatusUrl` | string | Relative URL to check the status of the associated workflow |

Returns an empty `runs` array if no executions have occurred yet.

**Error Responses:**

| Status | Condition |
|---|---|
| `404 Not Found` | Schedule does not exist, or exists but belongs to a different user |

---

## Status Codes Summary

| Code | Meaning |
|---|---|
| `200 OK` | Request succeeded |
| `201 Created` | Resource was created successfully |
| `400 Bad Request` | Request body or parameters failed validation, or operation is not valid for the current state |
| `401 Unauthorized` | JWT is missing or malformed |
| `403 Forbidden` | JWT `email_verified` claim is `false` |
| `404 Not Found` | Resource does not exist or belongs to a different user |
| `429 Too Many Requests` | Per-user schedule limit (10) has been reached |
