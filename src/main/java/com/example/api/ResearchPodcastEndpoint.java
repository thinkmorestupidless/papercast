package com.example.api;

import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.MediaTypes;
import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpException;
import akka.javasdk.http.HttpResponses;
import com.example.application.PodcastCreationWorkflow;
import com.example.domain.PodcastCreationState;
import com.example.domain.PodcastScript;
import com.typesafe.config.Config;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

@HttpEndpoint("/podcast")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
public class ResearchPodcastEndpoint {

    // --- Request / Response records ---

    public record CreatePodcastRequest(String query) {}

    public record CreatePodcastResponse(String workflowId, String statusUrl) {}

    public record StatusResponse(
            String status,
            int papersFound,
            int summariesGenerated,
            boolean scriptReady,
            String errorMessage
    ) {}

    public record ScriptResponse(PodcastScript script) {}

    // --- Fields ---

    private final ComponentClient componentClient;
    private final String elevenLabsApiKey;
    private final String elevenLabsVoiceId;
    private final String elevenLabsModelId;

    public ResearchPodcastEndpoint(ComponentClient componentClient, Config config) {
        this.componentClient   = componentClient;
        this.elevenLabsApiKey  = config.hasPath("elevenlabs.api-key")  ? config.getString("elevenlabs.api-key")  : null;
        this.elevenLabsVoiceId = config.hasPath("elevenlabs.voice-id") ? config.getString("elevenlabs.voice-id") : "21m00Tcm4TlvDq8ikWAM";
        this.elevenLabsModelId = config.hasPath("elevenlabs.model-id") ? config.getString("elevenlabs.model-id") : "eleven_turbo_v2_5";
    }

    // --- POST /podcast ---

    @Post
    public HttpResponse createPodcast(CreatePodcastRequest request) {
        if (request.query() == null || request.query().isBlank()) {
            return HttpResponses.badRequest("Query must not be blank");
        }
        String workflowId = UUID.randomUUID().toString();
        componentClient
                .forWorkflow(workflowId)
                .method(PodcastCreationWorkflow::create)
                .invoke(new PodcastCreationWorkflow.CreateCommand(request.query()));
        return HttpResponses.created(new CreatePodcastResponse(
                workflowId, "/podcast/" + workflowId + "/status"));
    }

    // --- GET /podcast/{id}/status ---

    @Get("/{id}/status")
    public StatusResponse getStatus(String id) {
        PodcastCreationState state = fetchState(id);
        return new StatusResponse(
                state.status().name(),
                state.papers().size(),
                state.summaries().size(),
                state.status() == PodcastCreationState.Status.COMPLETE,
                state.errorMessage()
        );
    }

    // --- GET /podcast/{id}/script ---

    @Get("/{id}/script")
    public HttpResponse getScript(String id) {
        PodcastCreationState state = fetchState(id);
        if (state.status() != PodcastCreationState.Status.COMPLETE) {
            return conflictResponse("Script not yet available. Current status: " + state.status().name());
        }
        return HttpResponses.ok(new ScriptResponse(state.script()));
    }

    // --- GET /podcast/{id}/audio ---

    @Get("/{id}/audio")
    public HttpResponse getAudio(String id) {
        PodcastCreationState state = fetchState(id);
        if (state.status() != PodcastCreationState.Status.COMPLETE) {
            return conflictResponse("Script not yet available — audio cannot be generated. Status: " + state.status().name());
        }
        if (elevenLabsApiKey == null || elevenLabsApiKey.isBlank()) {
            return HttpResponses.internalServerError("Audio generation is not configured (ELEVENLABS_API_KEY not set)");
        }
        String scriptText = buildScriptText(state.script());
        try {
            byte[] audioBytes = callElevenLabs(scriptText);
            return HttpResponses.of(StatusCodes.OK, ContentTypes.create(MediaTypes.AUDIO_MPEG), audioBytes);
        } catch (Exception e) {
            return HttpResponse.create()
                    .withStatus(StatusCodes.BAD_GATEWAY)
                    .withEntity(ContentTypes.APPLICATION_JSON,
                            ("{\"error\":\"Audio generation failed — script is still available at /podcast/" + id + "/script\"}").getBytes(StandardCharsets.UTF_8));
        }
    }

    // --- Helpers ---

    private PodcastCreationState fetchState(String workflowId) {
        try {
            return componentClient
                    .forWorkflow(workflowId)
                    .method(PodcastCreationWorkflow::getStatus)
                    .invoke();
        } catch (Exception e) {
            throw HttpException.notFound();
        }
    }

    private HttpResponse conflictResponse(String message) {
        return HttpResponse.create()
                .withStatus(StatusCodes.CONFLICT)
                .withEntity(ContentTypes.APPLICATION_JSON,
                        ("{\"error\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8));
    }

    private String buildScriptText(PodcastScript script) {
        var sb = new StringBuilder();
        sb.append(script.introduction()).append("\n\n");
        for (var segment : script.segments()) {
            sb.append(segment.narrative()).append("\n\n");
        }
        sb.append(script.conclusion());
        return sb.toString();
    }

    private byte[] callElevenLabs(String text) throws Exception {
        String jsonBody = "{\"text\":" + quoted(text)
                + ",\"model_id\":" + quoted(elevenLabsModelId)
                + ",\"output_format\":\"mp3_44100_128\"}";

        String url = "https://api.elevenlabs.io/v1/text-to-speech/" + elevenLabsVoiceId + "/stream";
        var httpClient = HttpClient.newHttpClient();
        var req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .header("xi-api-key", elevenLabsApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        var resp = httpClient.send(req, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() == 200) {
            return resp.body();
        }
        throw new RuntimeException("ElevenLabs returned status " + resp.statusCode());
    }

    private String quoted(String s) {
        return "\"" + s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r") + "\"";
    }
}
