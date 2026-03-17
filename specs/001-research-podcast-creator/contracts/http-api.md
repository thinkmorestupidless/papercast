# HTTP API Contract: Research Podcast Creator

**Base path**: `/podcast`
**Content-Type**: `application/json` (except audio endpoint)

---

## POST /podcast

Start the podcast creation pipeline for a search query.

**Request body**:
```json
{
  "query": "black hole imaging"
}
```

| Field | Type | Required | Constraints |
|---|---|---|---|
| `query` | string | yes | Non-blank; max 500 characters |

**Response — 201 Created**:
```json
{
  "workflowId": "a3f7c821-...",
  "statusUrl": "/podcast/a3f7c821-.../status"
}
```

**Response — 400 Bad Request** (empty or invalid query):
```json
{
  "error": "Query must not be blank"
}
```

---

## GET /podcast/{workflowId}/status

Poll the current pipeline status. Safe to call repeatedly.

**Path parameter**: `workflowId` — the ID returned from `POST /podcast`

**Response — 200 OK**:
```json
{
  "status": "SUMMARISING",
  "papersFound": 9,
  "summariesGenerated": 4,
  "scriptReady": false,
  "errorMessage": null
}
```

| Field | Type | Notes |
|---|---|---|
| `status` | string | One of: `CREATED`, `SEARCHING`, `SUMMARISING`, `SCRIPTING`, `COMPLETE`, `FAILED` |
| `papersFound` | int | Number of papers retrieved after deduplication; 0 until `SEARCHING` completes |
| `summariesGenerated` | int | Number of summaries generated so far; 0 until `SUMMARISING` begins |
| `scriptReady` | boolean | `true` only when status is `COMPLETE` |
| `errorMessage` | string or null | Set when `status` is `FAILED`; null otherwise |

**Response — 404 Not Found** (unknown workflowId):
```json
{
  "error": "Podcast session not found"
}
```

---

## GET /podcast/{workflowId}/script

Retrieve the generated podcast script. Only available when `scriptReady == true`.

**Response — 200 OK**:
```json
{
  "script": {
    "topic": "black hole imaging",
    "introduction": "Welcome to today's episode...",
    "segments": [
      {
        "paperId": "doi:10.1038/s41586-019-1141-x",
        "paperTitle": "First M87 Event Horizon Telescope Results...",
        "narrative": "In a landmark study published in 2019..."
      }
    ],
    "conclusion": "Today's research paints a picture of..."
  }
}
```

**Response — 404 Not Found** (unknown workflowId):
```json
{
  "error": "Podcast session not found"
}
```

**Response — 409 Conflict** (pipeline not yet complete):
```json
{
  "error": "Script not yet available",
  "currentStatus": "SUMMARISING"
}
```

---

## GET /podcast/{workflowId}/audio

Generate and stream a podcast audio recording from the completed script. Calls ElevenLabs on demand and streams the result directly to the caller.

**Response — 200 OK**:
- `Content-Type: audio/mpeg`
- Body: binary MP3 audio stream (chunked transfer encoding)

**Response — 404 Not Found** (unknown workflowId):
```json
{
  "error": "Podcast session not found"
}
```

**Response — 409 Conflict** (script not ready):
```json
{
  "error": "Script not yet available — audio cannot be generated",
  "currentStatus": "SCRIPTING"
}
```

**Response — 502 Bad Gateway** (ElevenLabs call failed):
```json
{
  "error": "Audio generation failed — script is still available at /podcast/{workflowId}/script"
}
```

---

## Error Response Shape (all endpoints)

```json
{
  "error": "Human-readable description of the problem",
  "currentStatus": "SCRIPTING"  // optional, included where relevant
}
```

---

## Access Control

All endpoints are restricted to internal service callers (no public internet access):
```java
@Acl(allow = @Acl.Matcher(service = "*"))
```

To expose to the public internet, change to:
```java
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
```
