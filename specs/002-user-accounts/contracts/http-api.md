# HTTP API Contracts: User Accounts

All endpoints require a valid Auth0 JWT in the `Authorization: Bearer <token>` header.
Missing or invalid tokens return `401 Unauthorized`.

---

## Authentication

All requests to `/podcast/*` and `/account/*` must include:
```
Authorization: Bearer <Auth0 JWT>
```

The JWT must contain a `sub` claim identifying the user. This claim is used as the entity key for the user's account.

---

## User Account Endpoints

### POST /account/queries
Save a query to the authenticated user's account.

**Request:**
```json
{ "queryText": "large language models" }
```

**Response 200 OK** — query saved (or already existed):
```json
{ "id": "550e8400-e29b-41d4-a716-446655440000", "queryText": "large language models", "savedAt": "2026-03-18T10:00:00Z" }
```

**Response 400 Bad Request** — blank query:
```json
{ "error": "Query text must not be blank" }
```

**Response 401 Unauthorized** — missing or invalid JWT.

---

### GET /account/queries
List all saved queries for the authenticated user, ordered oldest-first.

**Response 200 OK:**
```json
{
  "queries": [
    { "id": "550e8400-...", "queryText": "large language models", "savedAt": "2026-03-18T10:00:00Z" },
    { "id": "660f9500-...", "queryText": "black hole imaging", "savedAt": "2026-03-18T11:00:00Z" }
  ]
}
```

Empty list if user has no saved queries (or account does not yet exist):
```json
{ "queries": [] }
```

**Response 401 Unauthorized** — missing or invalid JWT.

---

### DELETE /account/queries/{queryId}
Delete a saved query by ID.

**Response 200 OK** — deleted:
```json
{ "message": "Query deleted" }
```

**Response 404 Not Found** — query ID does not exist for this user:
```json
{ "error": "Query not found" }
```

**Response 401 Unauthorized** — missing or invalid JWT.

---

### POST /account/queries/{queryId}/run
Launch a podcast creation workflow using the text of a saved query.

**Response 201 Created** — workflow started:
```json
{ "workflowId": "abc123", "statusUrl": "/podcast/abc123/status" }
```

**Response 404 Not Found** — query ID does not exist for this user:
```json
{ "error": "Query not found" }
```

**Response 401 Unauthorized** — missing or invalid JWT.

---

## Changes to Existing Podcast Endpoints

All existing podcast endpoints (`POST /podcast`, `GET /podcast/{id}/status`, `GET /podcast/{id}/script`, `GET /podcast/{id}/audio`) now require authentication.

Behaviour is otherwise unchanged. The `sub` claim from the JWT is available in the endpoint but not stored in the workflow state.

---

## Error Format

All error responses use the same JSON envelope:
```json
{ "error": "<human-readable message>" }
```
