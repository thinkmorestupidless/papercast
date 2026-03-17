# Feature Specification: Research Podcast Creator

**Feature Branch**: `001-research-podcast-creator`
**Created**: 2026-03-17
**Status**: Draft

## Overview

A user submits a search query and the system searches online scientific paper archives, summarises the returned papers, generates a structured podcast script from those summaries, and produces an audio recording of the podcast using ElevenLabs text-to-speech.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Search and Summarise Scientific Papers (Priority: P1)

A researcher or curious reader enters a topic or keywords and receives a list of relevant scientific papers, each with a plain-language summary they can read and understand without specialist knowledge.

**Why this priority**: This is the foundation of the feature. Without a working search-and-summarise pipeline, the podcast creation steps have no input. It also delivers independent value to users who only want reading summaries.

**Independent Test**: Can be fully tested by submitting a search query and verifying that a list of papers with summaries is returned, without any podcast generation.

**Acceptance Scenarios**:

1. **Given** the user submits the query "black hole imaging", **When** the search completes, **Then** the system returns up to 10 relevant papers, each with a title, authors, publication date, source link, and a plain-language summary of 2–4 sentences.
2. **Given** the user submits a very specific or obscure query that returns no results, **When** the search completes, **Then** the system informs the user that no papers were found and suggests broadening the search terms.
3. **Given** the user submits an empty or whitespace-only query, **When** they attempt to search, **Then** the system rejects the request and prompts for a valid query.

---

### User Story 2 - Generate a Podcast Script (Priority: P2)

After reviewing the summarised papers, the user requests a podcast script. The system produces a coherent, conversational narrative that introduces the topic, walks through each paper's key findings, and closes with a summary of the overall research landscape.

**Why this priority**: The script generation bridges the raw summaries into an engaging narrative format. It can be validated independently without requiring audio generation.

**Independent Test**: Can be fully tested by verifying that a readable, coherent podcast script is returned after a successful paper search, covering all returned paper summaries in a logical flow.

**Acceptance Scenarios**:

1. **Given** a set of summarised papers has been returned, **When** the user requests a podcast script, **Then** the system returns a structured script with an introduction, one segment per paper covering its key findings, and a closing summary.
2. **Given** a single paper was returned in the search, **When** the user requests a podcast script, **Then** the system generates a valid single-paper episode script rather than failing.
3. **Given** the system has previously generated a script for the same search, **When** the user requests a new script, **Then** the system generates a fresh script (results may vary naturally due to AI generation).

---

### User Story 3 - Generate a Podcast Audio Recording (Priority: P3)

After a podcast script has been created, the user requests an audio recording. The system sends the script to ElevenLabs and returns a playable audio file of the podcast.

**Why this priority**: This is the final delivery step and depends on both P1 and P2 being complete. It integrates with an external service (ElevenLabs) and delivers the fully-formed podcast experience.

**Independent Test**: Can be fully tested by providing a completed script and verifying that a playable audio file is returned, with the voice reading the script content accurately.

**Acceptance Scenarios**:

1. **Given** a podcast script has been generated, **When** the user requests an audio recording, **Then** the system returns a downloadable or streamable audio file of the script read aloud.
2. **Given** the ElevenLabs service is unavailable, **When** the user requests an audio recording, **Then** the system informs the user that audio generation failed and that the text script is still available.
3. **Given** a podcast script is very long (e.g. covers 10 papers), **When** the user requests an audio recording, **Then** the system successfully generates the full recording without truncation.

---

### Edge Cases

- What happens when a paper has no abstract available in the archive?
- How does the system handle non-English papers returned in search results?
- What if ElevenLabs returns a partial or corrupted audio file?
- What happens when the search archive returns duplicate papers from different sources?
- How does the system behave when the search query returns papers from wildly different sub-fields, making a coherent script difficult?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST allow users to submit a free-text search query for scientific papers.
- **FR-002**: System MUST search across multiple open-access scientific paper archives — arXiv, PubMed, and Semantic Scholar — aggregating and deduplicating results to return up to 10 relevant papers per query.
- **FR-003**: System MUST generate a plain-language summary (2–4 sentences) for each paper returned.
- **FR-004**: System MUST generate a structured podcast script from the set of paper summaries, covering an introduction, per-paper segments, and a closing summary.
- **FR-005**: System MUST send the podcast script to ElevenLabs and return an audio recording of the podcast.
- **FR-006**: System MUST make the audio recording available for the user to play or download.
- **FR-007**: System MUST inform the user with a clear message if no papers are found for their query.
- **FR-008**: System MUST inform the user if audio generation fails, and MUST still make the text script available in that case.
- **FR-009**: System MUST reject empty or invalid search queries with a user-friendly error message.
- **FR-010**: System MUST handle papers with missing abstracts gracefully, either by skipping them or by noting the omission in the summary.

### Key Entities

- **SearchQuery**: The user's free-text input used to retrieve relevant papers from the archive.
- **Paper**: A scientific paper result, including title, authors, publication date, archive source, URL, and abstract text.
- **PaperSummary**: A condensed, plain-language overview of a single paper's key findings, derived from the abstract.
- **PodcastScript**: A structured narrative document composed of an introduction, one segment per summarised paper, and a closing section.
- **PodcastRecording**: The audio file produced by ElevenLabs from the podcast script; associated with the script that generated it.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users receive summarised paper results within 60 seconds of submitting a search query.
- **SC-002**: Each paper summary accurately reflects the paper's stated key findings, as verifiable against the source abstract.
- **SC-003**: The generated podcast script covers all returned paper summaries and reads as a coherent narrative without gaps or repetition.
- **SC-004**: Audio recording is delivered to the user within 120 seconds of requesting it.
- **SC-005**: The audio recording faithfully narrates the full podcast script without truncation or missing sections.
- **SC-006**: Users can successfully complete the full flow — search, summarise, generate script, generate audio — in a single uninterrupted session.
- **SC-007**: When external services are unavailable, users receive clear error messages and retain access to any already-generated artefacts (summaries, script).

## Assumptions

- A single host voice is used for the podcast recording (no multi-speaker dialogue format).
- Up to 10 papers are returned per search; the podcast script covers all returned papers.
- Summaries and scripts are generated by an AI language model.
- Papers are sourced from open-access archives with publicly accessible abstracts.
- The user interacts with the system via an HTTP API or web interface.
- Audio files are in a common format (e.g. MP3) compatible with standard media players.
