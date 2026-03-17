# Research: Research Podcast Creator

**Branch**: `001-research-podcast-creator` | **Date**: 2026-03-17

---

## 1. Scientific Paper Archive APIs

### Decision: Search Semantic Scholar first, supplement with arXiv and PubMed

**Rationale**: Semantic Scholar returns structured JSON with cross-reference IDs (`externalIds`) that map each paper to its arXiv ID, DOI, and PMID in a single call. This makes it the natural deduplication hub. arXiv and PubMed are queried to fill gaps in coverage (especially for medical/life-sciences papers from PubMed and preprints from arXiv).

**Alternatives considered**:
- Query all three in parallel then deduplicate: More complex, higher latency, greater risk of rate-limiting without API keys.
- arXiv only: Covers physics, maths, CS, quantitative biology well but misses clinical/medical literature and many published papers that never appear on arXiv.

---

### arXiv API

| Property | Value |
|---|---|
| Base URL | `http://export.arxiv.org/api/` |
| Search endpoint | `GET /query?search_query={term}&max_results=10&sortBy=relevance` |
| Response format | Atom 1.0 XML |
| Authentication | None required |
| Rate limit | 1 request / 3 seconds (soft limit; violations risk IP block) |
| Unique identifier | arXiv ID embedded in `<id>` element — strip URL prefix and version suffix |

**Java integration notes**:
- Parse Atom XML with `javax.xml.parsers` (DOM/SAX) or any Atom-aware library.
- Strip URL prefix (`http://arxiv.org/abs/`) and version suffix (e.g. `v1`) from the `<id>` element to get the canonical arXiv ID.
- Use `<summary>` for abstract text, `<author>` for author list, `<published>` for date.

---

### PubMed / NCBI E-utilities API

| Property | Value |
|---|---|
| Base URL | `https://eutils.ncbi.nlm.nih.gov/entrez/eutils/` |
| Search endpoint (step 1) | `GET /esearch.fcgi?db=pubmed&term={term}&retmax=10&retmode=json` |
| Fetch endpoint (step 2) | `GET /efetch.fcgi?db=pubmed&id={pmids}&retmode=xml` |
| Response format | JSON (ESearch), XML (EFetch) |
| Authentication | None required; API key recommended for rate increase |
| Rate limit (no key) | 3 requests/second; exceeding blocks IP |
| Rate limit (with key) | 10 requests/second |
| Unique identifier | PMID (numeric string) |

**Java integration notes**:
- Two-step: ESearch to get PMIDs, then EFetch to get abstracts and metadata.
- Include `tool=research-podcast-creator&email=<contact>` parameters in all requests per NCBI policy.
- Register a free API key at ncbi.nlm.nih.gov/account for higher rate limits.

---

### Semantic Scholar Graph API

| Property | Value |
|---|---|
| Base URL | `https://api.semanticscholar.org` |
| Search endpoint | `GET /graph/v1/paper/search?query={term}&limit=10&fields=externalIds,title,abstract,authors,year,publicationDate,url` |
| Response format | JSON |
| Authentication | None required; API key recommended |
| Rate limit (no key) | 5,000 requests/5 min (shared pool) |
| Rate limit (with key) | 1 request/second (dedicated; free key) |
| Unique identifier | `paperId` (S2 internal); `externalIds` maps to DOI, arXiv, PMID |

**Java integration notes**:
- Request the `externalIds` field always — it is the cornerstone of deduplication.
- The `abstract` field may be null for some papers; handle gracefully.
- Free API keys available at semanticscholar.org/product/api.

---

### Decision: Deduplication Strategy

Use a canonical identifier hierarchy: `DOI > PMID > ArXiv ID > Semantic Scholar paperId`

Steps:
1. Query Semantic Scholar first; extract `externalIds` for each result.
2. Query arXiv and PubMed using IDs found in Semantic Scholar results to supplement metadata.
3. Build a deduplication map keyed by canonical ID. If two results share any identifier, merge into one record preferring the most complete metadata.
4. Normalise DOIs (lowercase, strip `https://doi.org/` prefix), arXiv IDs (strip URL prefix, strip version suffix), and PMIDs (trim whitespace).
5. Fallback: title + first-author last name (normalised, lowercase, punctuation stripped) for papers with no shared identifiers. Use only as last resort.

---

## 2. ElevenLabs Text-to-Speech API

### Decision: Use `eleven_turbo_v2_5` model with streaming endpoint

**Rationale**: `eleven_turbo_v2_5` supports up to 40,000 characters per request — well above the expected podcast script length (approximately 3,000–5,000 characters for a 10-paper episode). Using the streaming endpoint (`/v1/text-to-speech/{voice_id}/stream`) allows the audio to be forwarded incrementally to the client rather than buffering the full file in memory.

**Alternatives considered**:
- `eleven_multilingual_v2` (5,000 char limit): Adequate for most scripts but may require chunking for long episodes; turbo model is safer and cheaper.
- Non-streaming endpoint: Requires full audio to be buffered before sending to client; worse latency, higher memory pressure.

| Property | Value |
|---|---|
| Non-streaming endpoint | `POST https://api.elevenlabs.io/v1/text-to-speech/{voice_id}` |
| Streaming endpoint | `POST https://api.elevenlabs.io/v1/text-to-speech/{voice_id}/stream` |
| Authentication | `xi-api-key` header |
| Default voice | Rachel (`21m00Tcm4TlvDq8ikWAM`) — calm, neutral, clear |
| Default model | `eleven_turbo_v2_5` |
| Character limit | 40,000 characters |
| Output format | `mp3_44100_128` (MP3, 44.1 kHz, 128 kbps) |

**Chunking** (if script exceeds 40,000 characters in exceptional cases):
- Split at sentence boundaries using pattern `[.!?]\s+(?=[A-Z])`.
- Target chunk size: ≤ 5,000 characters for consistent latency.
- Pass `previous_request_ids` (up to 3) and `previous_text`/`next_text` to preserve natural prosody across chunk boundaries.
- Concatenate MP3 byte arrays from all chunks — MP3 decoders handle frame sync automatically.

**Voice ID configuration**: Configurable via `application.conf` (`elevenlabs.voice-id`). Default: Rachel. Operators can override with any premade or custom voice ID.

---

## 3. Akka SDK Component Architecture

### Decision: Workflow orchestrates steps 1–3; endpoint streams audio on demand

**Rationale**: The workflow tracks the multi-step process (search → summarise → script) and preserves intermediate results in its state. Audio generation is handled on-demand by the endpoint: when the user requests the audio, the endpoint retrieves the completed script from the workflow and makes a synchronous streaming call to ElevenLabs. This avoids storing large binary audio data in workflow state.

**Alternatives considered**:
- Workflow includes audio step: Requires storing the MP3 (10–15 MB) in workflow state, which is wasteful for potentially regenerated recordings.
- Endpoint handles all steps: Loses progress tracking and fault tolerance provided by the workflow.

### Akka Components

| Component | Type | Responsibility |
|---|---|---|
| `PodcastCreationWorkflow` | Workflow | Orchestrates search → summarise → script pipeline; persists intermediate state |
| `PaperSummaryAgent` | Agent | Accepts a batch of paper abstracts; returns plain-language summaries |
| `PodcastScriptAgent` | Agent | Accepts summaries; returns a structured podcast script |
| `ResearchPodcastEndpoint` | HTTP Endpoint | Exposes REST API; streams ElevenLabs audio on demand |

### Workflow Steps

| Step method | Action | Timeout |
|---|---|---|
| `searchPapersStep` | HTTP calls to Semantic Scholar, arXiv, PubMed; deduplicate; top 10 | 30s |
| `summarisePapersStep` | Call `PaperSummaryAgent` with all papers in batch | 60s |
| `generateScriptStep` | Call `PodcastScriptAgent` with summaries | 60s |

### Agent Session Strategy

Both agents share the workflow ID as their session ID. This ensures context continuity — the script agent can implicitly draw on the context established during summarisation.

---

## 4. External Dependency Justification (Constitution I)

| Dependency | Justification |
|---|---|
| arXiv HTTP API | No Akka SDK primitive provides academic paper search; standard HTTP client integration |
| PubMed E-utilities API | Same justification as arXiv |
| Semantic Scholar Graph API | Same justification as arXiv |
| ElevenLabs TTS API | No Akka SDK primitive provides text-to-speech; standard HTTP client integration |
| Java DOM/SAX XML parser (JDK built-in) | Required for arXiv Atom XML parsing; already in JDK, no additional dependency |

All external API calls use Java's built-in `java.net.http.HttpClient` or Akka's injected `HttpClient` — no additional HTTP client library dependency.
