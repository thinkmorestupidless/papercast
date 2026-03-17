# Quickstart: Research Podcast Creator

## Prerequisites

- Akka CLI installed
- Java 21+, Maven 3.9+
- API keys: ElevenLabs (`ELEVENLABS_API_KEY`), NCBI/PubMed (`NCBI_API_KEY`), Semantic Scholar (`S2_API_KEY`)

## Configuration

Add to `src/main/resources/application.conf`:

```hocon
elevenlabs {
  api-key = ${ELEVENLABS_API_KEY}
  voice-id = "21m00Tcm4TlvDq8ikWAM"   # Rachel (default)
  model-id = "eleven_turbo_v2_5"
}

paper-search {
  ncbi-api-key = ${NCBI_API_KEY}
  ncbi-tool    = "research-podcast-creator"
  ncbi-email   = "your-email@example.com"
  s2-api-key   = ${S2_API_KEY}          # optional; improves rate limits
  max-results  = 10
}
```

## Run locally

```bash
export ELEVENLABS_API_KEY=your_key_here
export NCBI_API_KEY=your_key_here
export S2_API_KEY=your_key_here

mvn compile exec:java
```

## Example: Full flow

### 1. Start the pipeline

```bash
curl -s -X POST http://localhost:9000/podcast \
  -H "Content-Type: application/json" \
  -d '{"query": "black hole imaging"}' | jq .
```

Response:
```json
{
  "workflowId": "a3f7c821-4b9e-4c12-8f01-d3e9f7a2b654",
  "statusUrl": "/podcast/a3f7c821-4b9e-4c12-8f01-d3e9f7a2b654/status"
}
```

### 2. Poll for status

```bash
curl -s http://localhost:9000/podcast/a3f7c821-.../status | jq .
```

Response (in progress):
```json
{
  "status": "SUMMARISING",
  "papersFound": 9,
  "summariesGenerated": 4,
  "scriptReady": false,
  "errorMessage": null
}
```

Response (complete):
```json
{
  "status": "COMPLETE",
  "papersFound": 9,
  "summariesGenerated": 9,
  "scriptReady": true,
  "errorMessage": null
}
```

### 3. Retrieve the script

```bash
curl -s http://localhost:9000/podcast/a3f7c821-.../script | jq .
```

### 4. Download the audio

```bash
curl -s http://localhost:9000/podcast/a3f7c821-.../audio \
  --output podcast-black-hole-imaging.mp3

# Or stream directly to a player
curl -s http://localhost:9000/podcast/a3f7c821-.../audio | ffplay -
```

## Expected timings

| Step | Typical duration |
|---|---|
| Paper search (3 archives) | 5–15 seconds |
| Summarisation (AI) | 10–30 seconds |
| Script generation (AI) | 10–20 seconds |
| Audio generation (ElevenLabs) | 15–60 seconds |
| **Total end-to-end** | **~60–120 seconds** |
