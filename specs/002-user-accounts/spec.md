# Feature Specification: User Accounts with Auth0 Authentication

**Feature Branch**: `002-user-accounts`
**Created**: 2026-03-18
**Status**: Draft
**Input**: User description: "add support for using Auth0 for authenticating users, so JWTs provided in HTTP calls can be used to identify users. Users can then store queries for creating podcasts in their account settings"

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Authenticated API Access (Priority: P1)

An authenticated user includes a JWT (issued by Auth0) in their HTTP requests. The system validates the token and identifies the user. Unauthenticated requests are rejected with a clear error. All existing podcast endpoints remain usable by authenticated users, with the caller's identity available for downstream use.

**Why this priority**: This is the security foundation — without identity verification, user-specific features (saved queries, personalisation) cannot be built safely. It also protects the service from anonymous abuse.

**Independent Test**: Send a `POST /podcast` request with a valid Auth0 JWT → request is accepted and processed. Send the same request without a token or with an expired token → request is rejected with 401.

**Acceptance Scenarios**:

1. **Given** a valid Auth0 JWT in the `Authorization: Bearer <token>` header, **When** any podcast endpoint is called, **Then** the request is processed normally and the user's identity is available to the system
2. **Given** no `Authorization` header, **When** any podcast endpoint is called, **Then** the system returns 401 Unauthorised
3. **Given** an expired or tampered JWT, **When** any podcast endpoint is called, **Then** the system returns 401 Unauthorised
4. **Given** a JWT from a different Auth0 tenant (wrong issuer), **When** any podcast endpoint is called, **Then** the system returns 401 Unauthorised

---

### User Story 2 — View Profile (Priority: P2)

An authenticated user can retrieve their stored profile — their user ID, display name, and email address as recorded from their Auth0 JWT.

**Why this priority**: Provides a readable view of the identity data the system is storing, enabling users and developers to verify that profile sync is working correctly.

**Independent Test**: Authenticate and call `GET /account/profile` → verify the response contains the correct `userId`, `name`, and `email` matching the JWT claims.

**Acceptance Scenarios**:

1. **Given** an authenticated user with a verified email, **When** they call `GET /account/profile`, **Then** the response contains their `userId` (Auth0 `sub`), `name`, and `email`
2. **Given** an authenticated user, **When** their name or email changes in Auth0 and they make any authenticated write, **Then** a subsequent `GET /account/profile` returns the updated values

---

### User Story 3 — Save and Manage Podcast Queries (Priority: P2)

An authenticated user can save podcast queries to their account so they can easily rerun them later. They can view their saved queries and delete ones they no longer need.

**Why this priority**: Saved queries are the core user-facing value of the accounts feature — they allow users to build up a personal research agenda without re-typing queries.

**Independent Test**: As an authenticated user, save a query → retrieve the list of saved queries → verify the saved query appears. Delete the query → verify it no longer appears in the list.

**Acceptance Scenarios**:

1. **Given** an authenticated user, **When** they save a query string, **Then** the query is stored against their account and a confirmation is returned
2. **Given** an authenticated user with saved queries, **When** they request their saved queries, **Then** all their saved queries are returned in the order they were saved
3. **Given** an authenticated user, **When** they save a duplicate query, **Then** only one copy is stored (duplicate is silently ignored)
4. **Given** an authenticated user with a saved query, **When** they delete it, **Then** it no longer appears in their saved queries list
5. **Given** an authenticated user, **When** they view their saved queries, **Then** they only see their own queries — not those of other users
6. **Given** an authenticated user, **When** they save an empty or blank query, **Then** the system rejects it with a clear error

---

### User Story 3 — Launch Podcast from Saved Query (Priority: P3)

An authenticated user can launch a podcast creation workflow directly from a saved query in their account, without having to copy and paste the query text manually.

**Why this priority**: Convenience feature that completes the workflow loop — save once, reuse many times. Depends on both P1 and P2 being in place.

**Independent Test**: Save a query, then call the "run saved query" endpoint with the saved query's ID → verify a new podcast workflow is started and the workflow ID is returned.

**Acceptance Scenarios**:

1. **Given** an authenticated user with a saved query, **When** they trigger a podcast run from that saved query, **Then** a new podcast workflow is created using the saved query text and the workflow ID is returned
2. **Given** an authenticated user, **When** they try to run a saved query that belongs to another user, **Then** the system returns 404 (does not reveal the existence of other users' data)
3. **Given** an authenticated user, **When** they try to run a saved query that does not exist, **Then** the system returns 404

---

### Edge Cases

- What happens when a JWT is valid but the user has never used the system before? → A user account is created automatically on first authenticated request (implicit registration)
- What happens if Auth0 is unreachable during token validation? → The system returns 503 Service Unavailable
- What happens if a user saves the same query twice? → Duplicate is silently ignored; the list remains deduplicated per user
- What happens when a user tries to save a blank query? → Rejected with 400 Bad Request
- What if a user deletes a saved query that does not exist? → Returns 404
- What if the JWT is valid but the user's email is not verified (`email_verified: false`)? → Returns 403 Forbidden
- What if the JWT is valid but `name` or `email` claims are absent? → Returns 400 Bad Request (indicates Auth0 Post-Login Action misconfiguration)

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST validate Auth0 JWTs presented in the `Authorization: Bearer <token>` header on all podcast-related endpoints
- **FR-002**: The system MUST extract the user identity (subject claim) from a validated JWT to identify the caller
- **FR-003**: The system MUST reject requests with missing, expired, or invalid JWTs with a 401 Unauthorised response
- **FR-004**: The system MUST automatically create a user account on the first authenticated request from a previously unseen user
- **FR-005**: Authenticated users MUST be able to save a query string to their account
- **FR-006**: Authenticated users MUST be able to retrieve the list of all their saved queries
- **FR-007**: Authenticated users MUST be able to delete a saved query from their account
- **FR-008**: Saved queries MUST be scoped to the owning user — users cannot read or modify other users' saved queries
- **FR-009**: The system MUST prevent duplicate saved queries for the same user (same query text stored only once per account)
- **FR-010**: Authenticated users MUST be able to launch a `POST /podcast` workflow using the text of a saved query, identified by that query's ID
- **FR-011**: The system MUST be configurable with the Auth0 tenant domain and audience so that token validation can be performed without a network call to Auth0 on every request
- **FR-012**: The system MUST extract `name` and `email` claims from the JWT and store/update them on the user's account on every authenticated write operation
- **FR-013**: The system MUST reject requests where the JWT's `email_verified` claim is `false` or absent, returning 403 Forbidden
- **FR-014**: Authenticated users MUST be able to retrieve their stored profile (`userId`, `name`, `email`) via `GET /account/profile`
- **FR-015**: The system MUST reject requests where the JWT is missing the `name` or `email` claims with 400 Bad Request

### Key Entities

- **User**: Represents an authenticated account. Identified by the Auth0 subject claim (`sub`). Stores `name` and `email` sourced from the JWT and updated on every authenticated write. Created automatically on first authenticated request. Owns zero or more saved queries.
- **SavedQuery**: A query string stored against a user account. Has an ID, the query text, and the date it was saved. Belongs to exactly one user.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Requests with valid Auth0 JWTs are accepted; requests with missing or invalid tokens are rejected — 100% enforcement with no false positives on valid tokens
- **SC-002**: Users can save, list, and delete queries in under 1 second per operation under normal load
- **SC-003**: A user's saved queries are never visible to any other user — verified by cross-user access tests returning 404
- **SC-004**: Token validation adds no more than 50ms overhead to authenticated requests (validated locally without a call to Auth0 on every request)
- **SC-005**: A new user's account is created automatically with no additional action required from the user

## Assumptions

- Auth0 is the sole identity provider; no username/password or other OAuth providers are in scope for this feature
- JWT validation is performed locally using Auth0's published public keys (JWKS endpoint fetched at startup or cached), not by calling Auth0 on every request
- The Auth0 tenant domain and API audience are provided via environment variables / service configuration
- Auth0 access tokens include `name` and `email` as custom claims added by an Auth0 Post-Login Action — these claims are not present by default and require tenant configuration
- Saved queries have no enforced maximum length beyond what is reasonable for a search query
- There is no admin interface for managing users; this is entirely self-service
- The existing `POST /podcast` endpoint will require authentication after this feature is implemented

## Clarifications

### Session 2026-03-18

- Q: When a user changes their name or email in Auth0, what should Akka do with the stored values? → A: Update name/email from JWT on every authenticated write operation
- Q: Should the system reject requests from users whose Auth0 `email_verified` claim is `false`? → A: Yes — reject with 403 Forbidden
- Q: How should the user's name be stored — as a single string or split into first/last name fields? → A: Single `name` string as provided by Auth0
- Q: Should the API expose a GET /account/profile endpoint so users can retrieve their stored profile (userId, name, email)? → A: Yes — add GET /account/profile
- Q: If a valid JWT is missing the `name` or `email` claims, what should the system do? → A: Reject with 400 Bad Request — both claims are required
