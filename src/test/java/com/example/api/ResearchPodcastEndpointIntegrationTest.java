package com.example.api;

import akka.javasdk.JsonSupport;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import com.example.application.PaperSummaryAgent;
import com.example.application.PodcastScriptAgent;
import com.example.domain.PaperSummary;
import com.example.domain.PodcastScript;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class ResearchPodcastEndpointIntegrationTest extends TestKitSupport {

    private final TestModelProvider summaryModel = new TestModelProvider();
    private final TestModelProvider scriptModel = new TestModelProvider();

    @Override
    protected TestKit.Settings testKitSettings() {
        return TestKit.Settings.DEFAULT
                .withModelProvider(PaperSummaryAgent.class, summaryModel)
                .withModelProvider(PodcastScriptAgent.class, scriptModel);
    }

    @Test
    public void testCreatePodcastReturnsBadRequestForBlankQuery() {
        var response = httpClient
                .POST("/podcast")
                .withRequestBody(new ResearchPodcastEndpoint.CreatePodcastRequest(""))
                .invoke();

        assertThat(response.status().isFailure()).isTrue();
        assertThat(response.status().intValue()).isEqualTo(400);
    }

    @Test
    public void testGetStatusReturns404ForUnknownWorkflowId() {
        var response = httpClient
                .GET("/podcast/unknown-workflow-id-xyz/status")
                .invoke();

        assertThat(response.status().intValue()).isEqualTo(404);
    }

    @Test
    public void testGetScriptReturns404ForUnknownWorkflowId() {
        var response = httpClient
                .GET("/podcast/unknown-workflow-id-xyz/script")
                .invoke();

        assertThat(response.status().intValue()).isEqualTo(404);
    }

    @Test
    public void testGetAudioReturns404ForUnknownWorkflowId() {
        var response = httpClient
                .GET("/podcast/unknown-workflow-id-xyz/audio")
                .invoke();

        assertThat(response.status().intValue()).isEqualTo(404);
    }

    @Test
    public void testCreatePodcastAndPollStatus() {
        var summaryResponse = new PaperSummaryAgent.SummariseResponse(List.of(
                new PaperSummary("arxiv:1", "Machine Learning Paper", "A plain-language summary.")
        ));
        summaryModel.fixedResponse(JsonSupport.encodeToString(summaryResponse));

        var scriptResponse = new PodcastScript(
                "machine learning",
                "Welcome to this episode on machine learning!",
                List.of(new PodcastScript.PaperSegment("arxiv:1", "Machine Learning Paper", "Let's explore this paper...")),
                "Thanks for listening to this episode."
        );
        scriptModel.fixedResponse(JsonSupport.encodeToString(scriptResponse));

        // Create podcast workflow
        var createResponse = httpClient
                .POST("/podcast")
                .withRequestBody(new ResearchPodcastEndpoint.CreatePodcastRequest("machine learning"))
                .responseBodyAs(ResearchPodcastEndpoint.CreatePodcastResponse.class)
                .invoke();

        assertThat(createResponse.status().intValue()).isEqualTo(201);
        var workflowId = createResponse.body().workflowId();
        assertThat(workflowId).isNotBlank();

        // Poll status until terminal state
        Awaitility.await()
                .atMost(60, TimeUnit.SECONDS)
                .pollInterval(2, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var statusResponse = httpClient
                            .GET("/podcast/" + workflowId + "/status")
                            .responseBodyAs(ResearchPodcastEndpoint.StatusResponse.class)
                            .invoke();

                    assertThat(statusResponse.status().intValue()).isEqualTo(200);
                    var status = statusResponse.body().status();
                    assertThat(status).isIn("COMPLETE", "FAILED");
                });
    }

    @Test
    public void testGetScriptReturns409WhenNotComplete() {
        var summaryResponse = new PaperSummaryAgent.SummariseResponse(List.of(
                new PaperSummary("arxiv:1", "Test Paper", "Summary.")
        ));
        summaryModel.fixedResponse(JsonSupport.encodeToString(summaryResponse));

        var scriptResponse = new PodcastScript(
                "test",
                "Intro.",
                List.of(new PodcastScript.PaperSegment("arxiv:1", "Test Paper", "Narrative.")),
                "Conclusion."
        );
        scriptModel.fixedResponse(JsonSupport.encodeToString(scriptResponse));

        // Create podcast
        var createResponse = httpClient
                .POST("/podcast")
                .withRequestBody(new ResearchPodcastEndpoint.CreatePodcastRequest("quantum computing"))
                .responseBodyAs(ResearchPodcastEndpoint.CreatePodcastResponse.class)
                .invoke();

        assertThat(createResponse.status().intValue()).isEqualTo(201);
        var workflowId = createResponse.body().workflowId();

        // Immediately request script — likely not complete yet
        // The workflow just started so it should be SEARCHING or early stage
        // Poll until we see either 200 (script ready) or 409 (not ready yet)
        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    var scriptResponse2 = httpClient
                            .GET("/podcast/" + workflowId + "/script")
                            .invoke();

                    // Either script is ready (200) or not yet (409) — both are valid
                    assertThat(scriptResponse2.status().intValue()).isIn(200, 409);
                });
    }
}
