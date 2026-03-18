# Research: User Accounts with Auth0 Authentication

## JWT Authentication in Akka SDK

### Decision: Use `@JWT` annotation with `AbstractHttpEndpoint`
- **Rationale**: Akka SDK has first-class JWT support. Annotating an endpoint class with `@JWT(validate = JWT.JwtMethodMode.BEARER_TOKEN)` tells the runtime to require and validate a bearer token on every request. Extending `AbstractHttpEndpoint` gives access to `requestContext().getJwtClaims()` to extract the validated `sub` claim.
- **Alternatives considered**: Manual `Authorization` header parsing — rejected (reimplements what the SDK provides, no benefit).

### Decision: Do not hardcode the Auth0 issuer in the annotation
- **Rationale**: `bearerTokenIssuers` in `@JWT` accepts a literal string. Issuer validation is better handled by the Akka service JWT key configuration (`akka services jwts add --issuer <auth0-issuer>`), which ties the key to the issuer at deploy time. This keeps the code portable across environments.
- **Alternatives considered**: Hardcoding `@JWT(bearerTokenIssuers = "https://tenant.auth0.com/")` — rejected (couples code to a specific tenant, breaks in dev/staging).

### Decision: Use `@JWT` at endpoint class level (not per-method)
- **Rationale**: All podcast and account endpoints require authentication. Class-level annotation avoids repetition and ensures no method is accidentally left unprotected.
- **Alternatives considered**: Per-method annotation — rejected (error-prone; easier to forget on new methods).

### Local Development
- Akka dev mode uses a `dev` key with `none` algorithm — signature validation is skipped, but claim presence/values are still checked.
- When calling locally or in integration tests, create an unsigned JWT: `base64({"alg":"none"}) + "." + base64({"sub":"user123","iss":"auth0-issuer"})`.

### Integration Test JWT Pattern
```java
private String bearerTokenWith(Map<String, String> claims) throws JsonProcessingException {
    String header = Base64.getEncoder().encodeToString("{\"alg\":\"none\"}".getBytes());
    byte[] jsonClaims = JsonSupport.getObjectMapper().writeValueAsBytes(claims);
    String payload = Base64.getEncoder().encodeToString(jsonClaims);
    return header + "." + payload;
}
```

### Production Auth0 Setup (deploy time)
Auth0 uses RS256 (RSA SHA-256). Steps to wire up at deploy time:
1. Download Auth0 public key (PEM) from `https://YOUR_DOMAIN.auth0.com/pem`
2. Create Akka asymmetric secret: `akka secrets create asymmetric auth0-public-key --public-key /path/to/auth0.pem`
3. Register key on the service: `akka services jwts add research-podcast-creator --key-id auth0 --algorithm RS256 --issuer https://YOUR_DOMAIN.auth0.com/ --secret auth0-public-key`

---

## User State Storage

### Decision: Key Value Entity for `UserAccountEntity`
- **Rationale**: User account state is a simple mutable record — a user ID plus a list of saved queries. No event history or audit trail is required. Key Value Entity is the correct Akka primitive: simpler, lower overhead, and a direct match to the access pattern (always read/write the whole user state).
- **Entity key**: Auth0 `sub` claim (e.g., `auth0|abc123`). Unique per user across all Auth0 tenants.
- **Alternatives considered**: Event Sourced Entity — rejected (no requirement for event history; adds complexity without benefit for this use case).

### Decision: Deduplication by query text on save
- **Rationale**: FR-009 requires duplicates to be silently ignored. The entity's `saveQuery` command handler checks whether the query text already exists before adding a new `SavedQuery` entry.

---

## API Design

### Decision: Separate `UserAccountEndpoint` for account/query management
- **Rationale**: Keeps the `ResearchPodcastEndpoint` focused on podcast creation. Avoids a god-endpoint. Aligns with the single-responsibility principle in the constitution.
- **Base path**: `/account`
- **Endpoints**: `POST /account/queries`, `GET /account/queries`, `DELETE /account/queries/{queryId}`, `POST /account/queries/{queryId}/run`

### Decision: Implicit user creation on first save
- **Rationale**: FR-004 requires automatic account creation. The `UserAccountEntity` starts from an empty state (null state for KVE). The first `saveQuery` command creates the account state. No explicit registration endpoint needed.

---

## Configuration

No new runtime config keys are required in `application.conf`. The Auth0 issuer is configured at deploy time via the Akka CLI JWT key setup (see Production Auth0 Setup above). The `sub` claim is used directly as the entity key — no mapping table needed.
