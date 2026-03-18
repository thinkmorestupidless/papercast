# Data Model: User Accounts with Auth0 Authentication

## Entities

### UserAccount (Key Value Entity state)

Stored in `UserAccountEntity`, keyed by the Auth0 `sub` claim.

| Field | Type | Notes |
|---|---|---|
| `userId` | `String` | Auth0 subject claim (`sub`). Also the entity key. |
| `savedQueries` | `List<SavedQuery>` | Ordered by insertion time, oldest first. Never null — empty list on creation. |

**Validation rules:**
- `userId` must not be blank
- `savedQueries` must not contain duplicates by `queryText`

**State transitions:**
- `null` → `UserAccount` on first `SaveQuery` command (implicit creation)
- `UserAccount` → `UserAccount` (with modified `savedQueries`) on `SaveQuery` or `DeleteQuery`

---

### SavedQuery (nested record within UserAccount)

| Field | Type | Notes |
|---|---|---|
| `id` | `String` | UUID generated at save time. Stable identifier for this query. |
| `queryText` | `String` | The search query string. Must not be blank. |
| `savedAt` | `Instant` | Timestamp when the query was saved. |

**Validation rules:**
- `queryText` must not be blank
- `id` must be unique within a user's saved queries list

---

## Commands (UserAccountEntity)

### SaveQuery
```
Input:  SaveQuery(String queryText)
Output: Done (on success) | error "Query text must not be blank" | error "Query already saved"
```
- If state is null, creates a new `UserAccount` with the query as the first entry
- If query text already exists (case-sensitive), silently returns Done (idempotent)
- Generates a new UUID for `id` and sets `savedAt` to current time

### DeleteQuery
```
Input:  DeleteQuery(String queryId)
Output: Done (on success) | error "Query not found"
```
- Removes the `SavedQuery` with matching `id` from the list
- If `queryId` does not exist, returns error (404 at endpoint level)

### GetAccount
```
Input:  (none)
Output: UserAccount | error "Account not found" (when state is null)
```
- Returns current state

---

## Relationships

```
UserAccount (1) ──── (0..*) SavedQuery
     │
     │ identity key = Auth0 sub
     │
Auth0 JWT sub claim
```

A `UserAccount` is never explicitly created — it comes into existence when the first query is saved. It is never deleted (queries are deleted, not accounts).

---

## Impact on Existing Domain

No changes to existing domain records (`Paper`, `PaperSummary`, `PodcastScript`, `PodcastCreationState`).

The `PodcastCreationWorkflow` is unchanged — the user identity is not stored in the workflow state (workflows remain anonymous). The association between a user and a workflow is managed at the endpoint layer via the saved query's `POST /run` action.
