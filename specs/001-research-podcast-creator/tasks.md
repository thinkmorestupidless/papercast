# Tasks: Research Podcast Creator

**Input**: Design documents from `specs/001-research-podcast-creator/`
**Branch**: `001-research-podcast-creator`
**Spec**: spec.md | **Plan**: plan.md | **Data model**: data-model.md | **Contracts**: contracts/http-api.md

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Exact file paths are included in all descriptions

---

## Phase 1: Setup

**Purpose**: Configuration and project structure needed by all components

- [X] T001 Create `src/main/resources/application.conf` with `elevenlabs` block (`api-key`, `voice-id`, `model-id`) and `paper-search` block (`ncbi-api-key`, `ncbi-tool`, `ncbi-email`, `s2-api-key`, `max-results`) as documented in plan.md Configuration section

**Checkpoint**: Project compiles (`mvn compile`)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Domain records used by all three user stories. MUST be complete before any story work begins.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T002 [P] Create `src/main/java/com/example/domain/Paper.java` — immutable record with fields: `String id`, `String title`, `List<String> authors`, `String publicationDate`, `Source source` (enum: `ARXIV`, `PUBMED`, `SEMANTIC_SCHOLAR`), `String url`, `String abstractText` (nullable)
- [X] T003 [P] Create `src/main/java/com/example/domain/PaperSummary.java` — immutable record with fields: `String paperId`, `String paperTitle`, `String summary`
- [X] T004 [P] Create `src/main/java/com/example/domain/PodcastScript.java` — immutable record with fields: `String topic`, `String introduction`, `List<PaperSegment> segments`, `String conclusion`; includes nested record `PaperSegment(String paperId, String paperTitle, String narrative)`
- [X] T005 Create `src/main/java/com/example/domain/PodcastCreationState.java` — immutable record with fields: `String query`, `List<Paper> papers`, `List<PaperSummary> summaries`, `PodcastScript script`, `Status status` (enum: `CREATED`, `SEARCHING`, `SUMMARISING`, `SCRIPTING`, `COMPLETE`, `FAILED`), `String errorMessage`; includes `withPapers`, `withSummaries`, `withScript`, `withError` with-style mutator methods

**Checkpoint**: All domain records compile cleanly; no Akka imports in domain package

---

## Phase 3: User Story 1 — Search and Summarise Scientific Papers (Priority: P1) 🎯 MVP

**Goal**: User submits a query, the system searches arXiv + PubMed + Semantic Scholar, deduplicates results, and generates plain-language summaries for each paper. Status can be polled via the API.

**Independent Test**: `POST /podcast` with `{"query": "black hole imaging"}` → poll `GET /{id}/status` until `"status": "COMPLETE"` → verify `papersFound > 0` and `summariesGenerated == papersFound`

### Implementation for User Story 1

- [X] T006 [P] [US1] Create `src/main/java/com/example/application/PaperSummaryAgent.java`
- [X] T007 [P] [US1] Create `src/main/java/com/example/application/PodcastCreationWorkflow.java`
- [X] T008 [US1] Add `searchPapersStep()` to `src/main/java/com/example/application/PodcastCreationWorkflow.java`
- [X] T009 [US1] Add `summarisePapersStep()` to `src/main/java/com/example/application/PodcastCreationWorkflow.java`
- [X] T010 [P] [US1] Create `src/main/java/com/example/api/ResearchPodcastEndpoint.java`

### Tests for User Story 1

- [X] T011 [P] [US1] Create `src/test/java/com/example/application/PaperSummaryAgentIntegrationTest.java`
- [X] T012 [US1] Create `src/test/java/com/example/application/PodcastCreationWorkflowIntegrationTest.java`

**Checkpoint**: `mvn test` passes. `POST /podcast` → poll → papers found and summaries generated independently verifiable.

---

## Phase 4: User Story 2 — Generate a Podcast Script (Priority: P2)

**Goal**: From the set of paper summaries produced in US1, the system generates a structured podcast script with introduction, per-paper narrative segments, and a conclusion.

**Independent Test**: After US1 completes, `GET /{id}/script` returns a JSON body containing `topic`, `introduction`, a `segments` array (one entry per paper), and `conclusion` — all non-blank

### Implementation for User Story 2

- [X] T013 [P] [US2] Create `src/main/java/com/example/application/PodcastScriptAgent.java`
- [X] T014 [US2] Add `generateScriptStep()` to `src/main/java/com/example/application/PodcastCreationWorkflow.java`
- [X] T015 [P] [US2] Add `@Get("/{id}/script")` `getScript(String id)` to `src/main/java/com/example/api/ResearchPodcastEndpoint.java`

### Tests for User Story 2

- [X] T016 [P] [US2] Create `src/test/java/com/example/application/PodcastScriptAgentIntegrationTest.java`

**Checkpoint**: `mvn test` passes. `GET /{id}/script` returns structured script independently verifiable after US1 completion.

---

## Phase 5: User Story 3 — Generate a Podcast Audio Recording (Priority: P3)

**Goal**: From the completed podcast script, stream an MP3 audio recording generated by ElevenLabs back to the caller.

**Independent Test**: After US1+US2 complete, `GET /{id}/audio` returns a response with `Content-Type: audio/mpeg` and a non-empty binary body that is a valid MP3 file

### Implementation for User Story 3

- [X] T017 [US3] Add `@Get("/{id}/audio")` `getAudio(String id)` to `src/main/java/com/example/api/ResearchPodcastEndpoint.java`

### Tests for User Story 3

- [X] T018 [US3] Create `src/test/java/com/example/api/ResearchPodcastEndpointIntegrationTest.java`

**Checkpoint**: `mvn verify` passes all integration tests. Full end-to-end flow independently verifiable.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Documentation and operational readiness

- [X] T019 Update `README.md` with required environment variables (`ELEVENLABS_API_KEY`, `NCBI_API_KEY`, `S2_API_KEY`), local run instructions (`mvn compile exec:java`), and example curl commands for the full flow: `POST /podcast` → poll `/status` → `GET /script` → `GET /audio --output podcast.mp3`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 — BLOCKS all user stories
- **US1 (Phase 3)**: Depends on Phase 2 — T006 and T007 start in parallel; T008/T009/T010/T011 follow
- **US2 (Phase 4)**: Depends on Phase 3 complete — T013/T015/T016 start in parallel; T014 follows T013
- **US3 (Phase 5)**: Depends on Phase 4 complete — T017 then T018
- **Polish (Phase 6)**: Depends on Phase 5 complete

### Task Count Summary

| Phase | Tasks | Status |
|---|---|---|
| Phase 1: Setup | 1 | ✅ Complete |
| Phase 2: Foundational | 4 | ✅ Complete |
| Phase 3: US1 | 7 | ✅ Complete |
| Phase 4: US2 | 4 | ✅ Complete |
| Phase 5: US3 | 2 | ✅ Complete |
| Phase 6: Polish | 1 | ✅ Complete |
| **Total** | **19** | ✅ All done |
