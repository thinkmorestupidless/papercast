# Data Model: Research Podcast Creator

**Branch**: `001-research-podcast-creator` | **Date**: 2026-03-17

---

## Domain Records (`domain` package)

### `Paper`

Represents a scientific paper retrieved from an archive.

| Field | Type | Notes |
|---|---|---|
| `id` | `String` | Canonical identifier (DOI preferred, else PMID, arXiv ID, or S2 paper ID) |
| `title` | `String` | Full paper title |
| `authors` | `List<String>` | Author names in display order |
| `publicationDate` | `String` | ISO date string (`yyyy-MM-dd`) or year only; null if unavailable |
| `source` | `Source` | Enum: `ARXIV`, `PUBMED`, `SEMANTIC_SCHOLAR` |
| `url` | `String` | Link to the paper on the archive |
| `abstractText` | `String` | Raw abstract; may be null for papers without accessible abstracts |

**Validation rules**:
- `id` and `title` must not be blank.
- `authors` must not be null (may be empty list if not available).
- Papers with null `abstractText` are retained in results but flagged; they cannot be summarised.

---

### `PaperSummary`

A plain-language summary of a single paper's key findings.

| Field | Type | Notes |
|---|---|---|
| `paperId` | `String` | Matches `Paper.id` |
| `paperTitle` | `String` | Denormalised for convenience |
| `summary` | `String` | 2–4 sentence plain-language summary |

**Validation rules**:
- `summary` must not be blank.

---

### `PodcastScript`

A structured narrative derived from paper summaries.

| Field | Type | Notes |
|---|---|---|
| `topic` | `String` | The original search query, used as the episode topic |
| `introduction` | `String` | Opening segment — sets context, introduces the topic |
| `segments` | `List<PaperSegment>` | One segment per summarised paper, in presentation order |
| `conclusion` | `String` | Closing summary of the research landscape |

#### `PodcastScript.PaperSegment` (nested record)

| Field | Type | Notes |
|---|---|---|
| `paperId` | `String` | Matches `PaperSummary.paperId` |
| `paperTitle` | `String` | Used as segment heading |
| `narrative` | `String` | Conversational treatment of the paper's key findings |

**Validation rules**:
- `segments` must not be empty.
- Each `narrative` must not be blank.

---

### `PodcastCreationState` (Workflow state)

The persisted state of a `PodcastCreationWorkflow` instance.

| Field | Type | Notes |
|---|---|---|
| `query` | `String` | The original search query submitted by the user |
| `papers` | `List<Paper>` | Papers retrieved after deduplication; empty until `SEARCHING` completes |
| `summaries` | `List<PaperSummary>` | Generated summaries; empty until `SUMMARISING` completes |
| `script` | `PodcastScript` | Generated script; null until `SCRIPTING` completes |
| `status` | `Status` | Current pipeline status (see below) |
| `errorMessage` | `String` | Human-readable error description; null if no error |

#### `PodcastCreationState.Status` (enum)

| Value | Meaning |
|---|---|
| `CREATED` | Workflow started, pipeline not yet begun |
| `SEARCHING` | Querying paper archives |
| `SUMMARISING` | Generating paper summaries via AI agent |
| `SCRIPTING` | Generating podcast script via AI agent |
| `COMPLETE` | All pipeline steps done; script is available |
| `FAILED` | One or more steps failed; `errorMessage` is set |

**State transitions**:
```
CREATED → SEARCHING → SUMMARISING → SCRIPTING → COMPLETE
                  ↘              ↘             ↘
                  FAILED         FAILED        FAILED
```

**Immutability helpers** (with-style methods on record):
- `withPapers(List<Paper>)` → returns new state with status `SUMMARISING`
- `withSummaries(List<PaperSummary>)` → returns new state with status `SCRIPTING`
- `withScript(PodcastScript)` → returns new state with status `COMPLETE`
- `withError(String message)` → returns new state with status `FAILED`

---

## Agent Request/Response Records (`application` package — inner records)

### `PaperSummaryAgent`

| Record | Fields | Notes |
|---|---|---|
| `SummariseRequest` | `List<Paper> papers` | All papers to be summarised in a single call |
| `SummariseResponse` | `List<PaperSummary> summaries` | One summary per paper in same order |

### `PodcastScriptAgent`

| Record | Fields | Notes |
|---|---|---|
| `ScriptRequest` | `String topic`, `List<PaperSummary> summaries` | Topic is the original search query |
| (returns) | `PodcastScript` | Structured script |

---

## API Request/Response Records (`api` package — inner records of endpoint)

### `ResearchPodcastEndpoint`

| Record | Fields | Notes |
|---|---|---|
| `CreatePodcastRequest` | `String query` | Submitted by user to start the pipeline |
| `CreatePodcastResponse` | `String workflowId`, `String statusUrl` | Returned on 201 Created |
| `StatusResponse` | `String status`, `int papersFound`, `int summariesGenerated`, `boolean scriptReady`, `String errorMessage` | Poll for progress |
| `ScriptResponse` | `PodcastScript script` | Returned when `scriptReady == true` |

---

## Entity Relationships

```
SearchQuery (user input)
    │
    ▼
PodcastCreationWorkflow (state: PodcastCreationState)
    │
    ├── 0..10 Paper ──────► PaperSummary (1:1)
    │                           │
    │                           ▼
    └──────────────────► PodcastScript
                              │
                              ▼
                         ElevenLabs audio (generated on demand, not persisted)
```
