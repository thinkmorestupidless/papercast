# Tasks: User Accounts with Auth0 Authentication

**Input**: Design documents from `specs/002-user-accounts/`
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/ ✅

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1–US4)
- Include exact file paths in descriptions

---

## Phase 1: Foundational (Blocking Prerequisites)

**Purpose**: Domain record and Key Value Entity — required by all user story phases before any can begin.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T001 Create `UserAccount` domain record with fields `userId`, `name`, `email`, `List<SavedQuery> savedQueries`; inner record `SavedQuery(String id, String queryText, Instant savedAt)`; static factory `create(String userId, String name, String email)`; methods `withProfileUpdated(name, email)`, `withQuerySaved(queryText)` (deduplicates by queryText, generates UUID + Instant.now()), `withQueryDeleted(queryId)` (throws IllegalArgumentException if not found), `hasQuery(queryText)`, `findQuery(queryId)` in `src/main/java/com/example/domain/UserAccount.java`

- [X] T002 Create `UserAccountEntity` Key Value Entity annotated `@Component(id = "user-account")` extending `KeyValueEntity<UserAccount>`; inner records: `UpsertProfile(String name, String email)`, `SaveQuery(String queryText, String name, String email)`, `DeleteQuery(String queryId, String name, String email)`, `ProfileResponse(String userId, String name, String email)`, `SavedQueryResponse(String id, String queryText, String savedAt)`, `SavedQueriesResponse(List<SavedQueryResponse> queries)`; command handlers: `upsertProfile(UpsertProfile)` → `Effect<ProfileResponse>` (creates account if null, always updates name/email), `saveQuery(SaveQuery)` → `Effect<SavedQueryResponse>` (updates profile + saves query, idempotent on duplicate queryText, error on blank), `deleteQuery(DeleteQuery)` → `Effect<Done>` (updates profile + removes query, error if not found), `getQueries()` → `Effect<SavedQueriesResponse>` (empty list if account null), `getProfile()` → `Effect<ProfileResponse>` (error if account null) in `src/main/java/com/example/application/UserAccountEntity.java`

- [X] T003 [P] Create `UserAccountEntityTest` unit tests using `KeyValueEntityTestKit<UserAccount, UserAccountEntity>`; test cases: upsertProfile on new entity creates account; saveQuery stores query in state; saveQuery with duplicate queryText returns same response and leaves state unchanged; saveQuery with blank queryText returns error; deleteQuery removes query from state; deleteQuery with unknown queryId returns error; getQueries on new entity returns empty list; getProfile on new entity returns error in `src/test/java/com/example/application/UserAccountEntityTest.java`

**Checkpoint**: Run `mvn test -pl . -Dtest=UserAccountEntityTest` — all 8 unit tests must pass before proceeding.

---

## Phase 2: User Story 1 — JWT Auth on Podcast Endpoints (Priority: P1) 🎯 MVP

**Goal**: All existing podcast endpoints require a valid Auth0 JWT. Unauthenticated requests return 401. The endpoint validates `email_verified` (→ 403) and `name`/`email` claim presence (→ 400).

**Independent Test**: Send `POST /podcast` without a token → 401. Send with a valid bearer token (email_verified=true, name+email present) → request proceeds normally.

- [X] T004 [US1] Modify `ResearchPodcastEndpoint` to extend `AbstractHttpEndpoint` (change class declaration) and add `@JWT(validate = JWT.JwtMethodMode.BEARER_TOKEN)` at class level; all existing methods remain unchanged in `src/main/java/com/example/api/ResearchPodcastEndpoint.java`

- [X] T005 [US1] Update `ResearchPodcastEndpointIntegrationTest` to add a `bearerTokenWith(Map<String, String> claims)` helper that builds an unsigned JWT (`base64({"alg":"none"}) + "." + base64(claims)`); update ALL existing `httpClient` calls to include `.withHeader("Authorization", "Bearer " + bearerToken)` where `bearerToken = bearerTokenWith(Map.of("sub", "user|test1", "email_verified", "true", "name", "Test User", "email", "test@example.com"))`; add new test asserting a request with no `Authorization` header returns 401 in `src/test/java/com/example/api/ResearchPodcastEndpointIntegrationTest.java`

**Checkpoint**: Run `mvn verify -Dtest=ResearchPodcastEndpointIntegrationTest` — all existing tests still pass with the updated tokens, and the no-token test returns 401.

---

## Phase 3: User Story 2 — View Profile (Priority: P2)

**Goal**: Authenticated users with verified emails can retrieve their stored profile (`userId`, `name`, `email`) via `GET /account/profile`. Invalid/missing claims return the appropriate error code.

**Independent Test**: POST to any write endpoint (to create the account), then `GET /account/profile` with the same JWT → response contains correct `userId`, `name`, `email`.

- [X] T006 [US2] Create `UserAccountEndpoint` annotated `@HttpEndpoint("/account")`, `@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))`, `@JWT(validate = JWT.JwtMethodMode.BEARER_TOKEN)`, extending `AbstractHttpEndpoint`; inject `ComponentClient`; add private helper `extractClaims()` that: (1) reads `sub` → userId via `requestContext().getJwtClaims().subject().get()`, (2) reads `email_verified` boolean custom claim — throws `HttpException.forbidden()` if false or absent, (3) reads `name` and `email` string custom claims — throws `HttpException.badRequest("...")` if either is absent; add `@Get("/profile")` method `getProfile()` that calls `extractClaims()` then `componentClient.forKeyValueEntity(userId).method(UserAccountEntity::getProfile).invoke()` returning the `ProfileResponse` (returns empty profile with name+email from JWT if entity not found, by calling `upsertProfile` first) in `src/main/java/com/example/api/UserAccountEndpoint.java`

- [X] T007 [US2] Create `UserAccountEndpointIntegrationTest` extending `TestKitSupport`; add `bearerTokenWith(Map<String, String> claims)` helper (same pattern as ResearchPodcastEndpointIntegrationTest); add tests: no token → 401 on `GET /account/profile`; email_verified=false → 403; missing name claim → 400; valid token → 200 with correct userId/name/email; updated name in JWT + any write + GET /profile → returns new name in `src/test/java/com/example/api/UserAccountEndpointIntegrationTest.java`

**Checkpoint**: Run `mvn verify -Dtest=UserAccountEndpointIntegrationTest` — profile tests pass.

---

## Phase 4: User Story 3 — Save and Manage Podcast Queries (Priority: P2)

**Goal**: Authenticated users can save, list, and delete podcast queries. Queries are scoped to the authenticated user. Duplicates are silently ignored. Blank queries return 400.

**Independent Test**: Save a query → 200 with query ID. GET /account/queries → list contains the query. DELETE /account/queries/{id} → 200. GET /account/queries → empty list.

- [X] T008 [US3] Add `record SaveQueryRequest(String queryText)` to `UserAccountEndpoint`; add `@Post("/queries")` method `saveQuery(SaveQueryRequest request)` that calls `extractClaims()` then `componentClient.forKeyValueEntity(userId).method(UserAccountEntity::saveQuery).invoke(new UserAccountEntity.SaveQuery(request.queryText(), claims.name(), claims.email()))` returning `SavedQueryResponse` in `src/main/java/com/example/api/UserAccountEndpoint.java`

- [X] T009 [US3] Add `@Get("/queries")` method `getQueries()` to `UserAccountEndpoint` that calls `extractClaims()` then `componentClient.forKeyValueEntity(userId).method(UserAccountEntity::getQueries).invoke()` returning `SavedQueriesResponse` in `src/main/java/com/example/api/UserAccountEndpoint.java`

- [X] T010 [US3] Add `@Delete("/queries/{queryId}")` method `deleteQuery(String queryId)` to `UserAccountEndpoint` that calls `extractClaims()` then `componentClient.forKeyValueEntity(userId).method(UserAccountEntity::deleteQuery).invoke(new UserAccountEntity.DeleteQuery(queryId, claims.name(), claims.email()))` — return `HttpResponses.ok()` on success or propagate 404 error from entity in `src/main/java/com/example/api/UserAccountEndpoint.java`

- [X] T011 [US3] Add saved-query integration tests to `UserAccountEndpointIntegrationTest`: save a query → 200 with id/queryText/savedAt; GET /account/queries → list contains the query; save duplicate → 200 and list still has one entry; save blank → 400; delete query → 200; GET after delete → empty list; cross-user isolation: user A saves query, user B DELETE /account/queries/{id} → 404 in `src/test/java/com/example/api/UserAccountEndpointIntegrationTest.java`

**Checkpoint**: Run `mvn verify -Dtest=UserAccountEndpointIntegrationTest` — all query CRUD tests pass.

---

## Phase 5: User Story 4 — Launch Podcast from Saved Query (Priority: P3)

**Goal**: Authenticated users can launch a podcast creation workflow using the text of a saved query by ID. Non-existent or other users' query IDs return 404.

**Independent Test**: Save a query, then POST /account/queries/{id}/run → 201 with workflowId. POST with unknown id → 404.

- [X] T012 [US4] Add `record CreatePodcastResponse(String workflowId, String statusUrl)` to `UserAccountEndpoint`; add `@Post("/queries/{queryId}/run")` method `runQuery(String queryId)` that: (1) calls `extractClaims()`, (2) calls `componentClient.forKeyValueEntity(userId).method(UserAccountEntity::getProfile).invoke()` to verify account exists, (3) calls `componentClient.forKeyValueEntity(userId).method(UserAccountEntity::getQueries).invoke()` to find the saved query by ID — throws `HttpException.notFound()` if absent, (4) calls `componentClient.forWorkflow(UUID.randomUUID().toString()).method(PodcastCreationWorkflow::create).invoke(new PodcastCreationWorkflow.CreateCommand(savedQuery.queryText()))` and returns `HttpResponses.created(new CreatePodcastResponse(workflowId, "/podcast/" + workflowId + "/status"))` in `src/main/java/com/example/api/UserAccountEndpoint.java`

- [X] T013 [US4] Add run-query integration tests to `UserAccountEndpointIntegrationTest`: save query + run → 201 with workflowId and statusUrl; run non-existent queryId → 404; cross-user: user A's queryId used by user B → 404 in `src/test/java/com/example/api/UserAccountEndpointIntegrationTest.java`

**Checkpoint**: Run `mvn verify -Dtest=UserAccountEndpointIntegrationTest` — all run-query tests pass.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T014 Run `mvn verify` (full suite including all integration tests) and confirm zero test failures in project root

---

## Dependencies & Execution Order

### Phase Dependencies

- **Foundational (Phase 1)**: No dependencies — start immediately
- **US1 (Phase 2)**: Depends on Phase 1 completion (T001, T002 must be done)
- **US2 (Phase 3)**: Depends on Phase 1 completion; can start in parallel with US1
- **US3 (Phase 4)**: Depends on Phase 1 + US2 (adds methods to UserAccountEndpoint)
- **US4 (Phase 5)**: Depends on Phase 1 + US3 (adds method to UserAccountEndpoint)
- **Polish (Phase 6)**: Depends on all phases complete

### User Story Dependencies

- **US1 (P1)**: Can start after Foundational — no story dependencies
- **US2 (P2)**: Can start after Foundational — creates UserAccountEndpoint, no US1 dependency
- **US3 (P2)**: Depends on US2 (adds methods to the endpoint US2 created)
- **US4 (P3)**: Depends on US3 (run uses saved queries from US3)

### Within Each Phase

- T001 and T003 can overlap (T003 tests the domain record; T002 depends on T001)
- T008, T009, T010 must be sequential (same file)
- T011 can start once T008–T010 are done

### Parallel Opportunities

- T003 [P] can start as soon as T001 is done (different file from T002)
- T004 and T006 can run in parallel (different files — ResearchPodcastEndpoint vs UserAccountEndpoint)
- T007 and T011 can be interleaved as endpoint methods are added

---

## Parallel Example: Phase 1 + Phase 2 Overlap

```bash
# Once T001 is done:
Task A: T002 — Create UserAccountEntity (depends on T001)
Task B: T003 — Create UserAccountEntityTest (depends on T001, different file from T002)

# Once Phase 1 is done:
Task A: T004 — Add @JWT to ResearchPodcastEndpoint
Task B: T006 — Create UserAccountEndpoint skeleton (different file)
```

---

## Implementation Strategy

### MVP First (US1 Only)

1. Complete Phase 1: Foundational (T001–T003)
2. Complete Phase 2: US1 — JWT on podcast endpoints (T004–T005)
3. **STOP and VALIDATE**: All existing podcast tests pass with JWT tokens; unauthenticated requests return 401
4. Deploy/demo if ready

### Incremental Delivery

1. Foundational → verify unit tests pass
2. US1 → existing service now secured
3. US2 → profile visible; identity data round-trips correctly
4. US3 → saved queries working end-to-end
5. US4 → one-click podcast from saved query

---

## Notes

- `UserAccountEntityTest` uses `KeyValueEntityTestKit` (not `TestKitSupport`) — name it `*Test.java` so Surefire runs it during `mvn test`
- `UserAccountEndpointIntegrationTest` uses `TestKitSupport` — name it `*IntegrationTest.java` so Failsafe runs it during `mvn verify`
- `bearerTokenWith` tokens for integration tests must include `sub`, `email_verified: true`, `name`, `email` — missing any of these will trigger 400/403
- The `extractClaims()` helper in `UserAccountEndpoint` is the single place that enforces FR-013 (email_verified) and FR-015 (missing claims); call it at the start of every endpoint method
- Profile is updated on every write (FR-012): `saveQuery` and `deleteQuery` carry `name`/`email` in their command records and the entity calls `withProfileUpdated()` before the query operation
