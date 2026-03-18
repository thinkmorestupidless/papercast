# Quickstart: User Accounts with Auth0 Authentication

## Prerequisites

- Service running locally (`mvn compile exec:java`)
- A test JWT — for local dev, create an unsigned one (see below)

## Create a test JWT (local dev)

```bash
# Header: {"alg":"none"}
HEADER=$(echo -n '{"alg":"none"}' | base64)
# Payload: {"sub":"user|test123","iss":"https://dev.auth0.com/"}
PAYLOAD=$(echo -n '{"sub":"user|test123","iss":"https://dev.auth0.com/"}' | base64)
TOKEN="$HEADER.$PAYLOAD"
```

## Save a query

```bash
curl -X POST http://localhost:9000/account/queries \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"queryText": "large language models"}'
```

Response:
```json
{ "id": "550e8400-e29b-41d4-a716-446655440000", "queryText": "large language models", "savedAt": "2026-03-18T10:00:00Z" }
```

## List saved queries

```bash
curl http://localhost:9000/account/queries \
  -H "Authorization: Bearer $TOKEN"
```

## Delete a saved query

```bash
curl -X DELETE http://localhost:9000/account/queries/550e8400-e29b-41d4-a716-446655440000 \
  -H "Authorization: Bearer $TOKEN"
```

## Launch a podcast from a saved query

```bash
curl -X POST http://localhost:9000/account/queries/550e8400-e29b-41d4-a716-446655440000/run \
  -H "Authorization: Bearer $TOKEN"
```

Response:
```json
{ "workflowId": "abc123", "statusUrl": "/podcast/abc123/status" }
```

Then poll as before:
```bash
curl http://localhost:9000/podcast/abc123/status \
  -H "Authorization: Bearer $TOKEN"
```

## Test that auth is enforced

```bash
# No token — should return 401
curl -X POST http://localhost:9000/podcast \
  -H 'Content-Type: application/json' \
  -d '{"query": "test"}'

# Wrong issuer — should return 401
WRONG_PAYLOAD=$(echo -n '{"sub":"user|test123","iss":"https://evil.com/"}' | base64)
WRONG_TOKEN="$HEADER.$WRONG_PAYLOAD"
curl http://localhost:9000/account/queries \
  -H "Authorization: Bearer $WRONG_TOKEN"
```

## Production Auth0 setup

```bash
# 1. Download your Auth0 public key
curl -o auth0.pem https://YOUR_DOMAIN.auth0.com/pem

# 2. Create the Akka secret
akka secrets create asymmetric auth0-public-key --public-key ./auth0.pem

# 3. Register the key with the service
akka services jwts add research-podcast-creator \
  --key-id auth0 \
  --algorithm RS256 \
  --issuer https://YOUR_DOMAIN.auth0.com/ \
  --secret auth0-public-key

# 4. Deploy the service (tokens will now be fully validated)
akka service deploy research-podcast-creator research-podcast-creator:latest --push
```
