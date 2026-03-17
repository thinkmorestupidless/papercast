# Research Podcast Creator

An Akka service that searches online scientific paper archives, summarises the papers using AI, generates a podcast script, and creates an audio recording via ElevenLabs text-to-speech.

## Architecture

- **PaperSearchService** — searches arXiv, PubMed, and Semantic Scholar; deduplicates results by canonical ID
- **PaperSummaryAgent** — AI agent that produces plain-language summaries of each paper
- **PodcastScriptAgent** — AI agent that generates an engaging podcast script from the summaries
- **PodcastCreationWorkflow** — orchestrates the three-step pipeline: search → summarise → script
- **ResearchPodcastEndpoint** — REST API to start a podcast and retrieve status, script, and audio

## Environment Variables

| Variable | Required | Description |
|---|---|---|
| `OPENAI_API_KEY` | **Yes** | OpenAI API key for the AI agents |
| `ELEVENLABS_API_KEY` | For audio | ElevenLabs API key for text-to-speech |
| `NCBI_API_KEY` | No | NCBI API key for higher PubMed rate limits |
| `S2_API_KEY` | No | Semantic Scholar API key for higher rate limits |

## Build and Run

Build the project:

```shell
mvn compile
```

Run tests:

```shell
mvn test
```

Run integration tests:

```shell
mvn verify
```

Start the service locally:

```shell
export OPENAI_API_KEY=<your-key>
export ELEVENLABS_API_KEY=<your-key>   # optional — only needed for /audio endpoint
mvn compile exec:java
```

## API Usage

### Create a podcast

```shell
curl -X POST http://localhost:9000/podcast \
  -H 'Content-Type: application/json' \
  -d '{"query": "black hole imaging"}'
```

Response (201 Created):
```json
{
  "workflowId": "abc123",
  "statusUrl": "/podcast/abc123/status"
}
```

### Poll status

```shell
curl http://localhost:9000/podcast/abc123/status
```

Response:
```json
{
  "status": "COMPLETE",
  "papersFound": 8,
  "summariesGenerated": 8,
  "scriptReady": true,
  "errorMessage": null
}
```

Possible status values: `CREATED`, `SEARCHING`, `SUMMARISING`, `SCRIPTING`, `COMPLETE`, `FAILED`

### Get the podcast script

```shell
curl http://localhost:9000/podcast/abc123/script
```

Response (200 OK when complete, 409 if still processing):
```json
{
  "script": {
    "topic": "black hole imaging",
    "introduction": "Welcome to...",
    "segments": [
      {
        "paperId": "arxiv:2301.12345",
        "paperTitle": "First Image of a Black Hole",
        "narrative": "In a landmark study..."
      }
    ],
    "conclusion": "Today we explored..."
  }
}
```

### Get audio (MP3)

Requires `ELEVENLABS_API_KEY` to be set.

```shell
curl http://localhost:9000/podcast/abc123/audio --output podcast.mp3
```

Returns `audio/mpeg` stream. Returns 409 if script is not yet ready, 502 if ElevenLabs is unavailable.

## Deploy to Akka Platform

Build container image:

```shell
mvn clean install -DskipTests
```

Install the `akka` CLI as documented in [Install Akka CLI](https://doc.akka.io/reference/cli/index.html).

Deploy the service:

```shell
akka service deploy research-podcast-creator research-podcast-creator:tag-name --push
```

Set secrets before deploying:

```shell
akka secret create generic podcast-secrets \
  --literal OPENAI_API_KEY=<key> \
  --literal ELEVENLABS_API_KEY=<key>
```

Refer to [Deploy and manage services](https://doc.akka.io/operations/services/deploy-service.html) for more information.
