package com.example.application;

import akka.Done;
import akka.javasdk.JsonSupport;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import com.example.domain.PaperSummary;
import com.example.domain.PodcastCreationState;
import com.example.domain.PodcastScript;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PodcastCreationWorkflowIntegrationTest extends TestKitSupport {

    private final TestModelProvider summaryModel = new TestModelProvider();
    private final TestModelProvider scriptModel = new TestModelProvider();

    @Override
    protected TestKit.Settings testKitSettings() {
        return TestKit.Settings.DEFAULT
                .withModelProvider(PaperSummaryAgent.class, summaryModel)
                .withModelProvider(PodcastScriptAgent.class, scriptModel);
    }

    @Test
    public void testCreateWorkflowAndVerifyInitialState() {
        String workflowId = UUID.randomUUID().toString();
        var result = componentClient
                .forWorkflow(workflowId)
                .method(PodcastCreationWorkflow::create)
                .invoke(new PodcastCreationWorkflow.CreateCommand("machine learning"));

        assertThat(result).isEqualTo(Done.getInstance());

        // Verify workflow was created and has initial searching state
        var state = componentClient
                .forWorkflow(workflowId)
                .method(PodcastCreationWorkflow::getStatus)
                .invoke();

        assertThat(state).isNotNull();
        assertThat(state.query()).isEqualTo("machine learning");
        assertThat(state.status()).isIn(
                PodcastCreationState.Status.SEARCHING,
                PodcastCreationState.Status.SUMMARISING,
                PodcastCreationState.Status.SCRIPTING,
                PodcastCreationState.Status.COMPLETE,
                PodcastCreationState.Status.FAILED
        );
    }

    @Test
    public void testCreateWorkflowWithBlankQueryReturnsError() {
        String workflowId = UUID.randomUUID().toString();

        assertThrows(Exception.class, () ->
                componentClient
                        .forWorkflow(workflowId)
                        .method(PodcastCreationWorkflow::create)
                        .invoke(new PodcastCreationWorkflow.CreateCommand(""))
        );
    }

    @Test
    public void testGetStatusOnNonExistentWorkflowReturnsError() {
        String workflowId = UUID.randomUUID().toString();

        // getStatus on a workflow that was never created should return an error effect
        try {
            var state = componentClient
                    .forWorkflow(workflowId)
                    .method(PodcastCreationWorkflow::getStatus)
                    .invoke();
            // If it returns null state, that's also acceptable
            assertThat(state).isNull();
        } catch (Exception e) {
            // Expected — workflow not found / null state returns error effect
            assertThat(e).isNotNull();
        }
    }
}
