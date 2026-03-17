package com.example.application;

import akka.javasdk.JsonSupport;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import com.example.domain.PaperSummary;
import com.example.domain.PodcastScript;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class PodcastScriptAgentIntegrationTest extends TestKitSupport {

    private final TestModelProvider scriptModel = new TestModelProvider();

    @Override
    protected TestKit.Settings testKitSettings() {
        return TestKit.Settings.DEFAULT
                .withModelProvider(PodcastScriptAgent.class, scriptModel);
    }

    @Test
    public void testGenerateScriptHappyPath() {
        var summaries = List.of(
                new PaperSummary("arxiv:1", "Quantum Entanglement", "Scientists discovered new quantum entanglement properties."),
                new PaperSummary("arxiv:2", "Black Holes", "Researchers imaged a black hole for the first time."),
                new PaperSummary("pmid:3", "Dark Matter", "A new approach to detecting dark matter was proposed.")
        );

        var expectedScript = new PodcastScript(
                "astrophysics",
                "Welcome to this episode on astrophysics!",
                List.of(
                        new PodcastScript.PaperSegment("arxiv:1", "Quantum Entanglement", "Today we explore quantum entanglement..."),
                        new PodcastScript.PaperSegment("arxiv:2", "Black Holes", "Next, we look at black holes..."),
                        new PodcastScript.PaperSegment("pmid:3", "Dark Matter", "Finally, dark matter detection...")
                ),
                "That wraps up our look at astrophysics research."
        );
        scriptModel.fixedResponse(JsonSupport.encodeToString(expectedScript));

        var result = componentClient
                .forAgent()
                .inSession("test-session-1")
                .method(PodcastScriptAgent::generateScript)
                .invoke(new PodcastScriptAgent.ScriptRequest("astrophysics", summaries));

        assertThat(result.introduction()).isNotBlank();
        assertThat(result.segments()).hasSize(3);
        assertThat(result.conclusion()).isNotBlank();
    }

    @Test
    public void testGenerateScriptSinglePaper() {
        var summaries = List.of(
                new PaperSummary("arxiv:1", "Single Paper", "Just one paper summary.")
        );

        var expectedScript = new PodcastScript(
                "single topic",
                "Welcome to this single-paper episode!",
                List.of(new PodcastScript.PaperSegment("arxiv:1", "Single Paper", "Let's talk about this paper...")),
                "That's all for today."
        );
        scriptModel.fixedResponse(JsonSupport.encodeToString(expectedScript));

        var result = componentClient
                .forAgent()
                .inSession("test-session-2")
                .method(PodcastScriptAgent::generateScript)
                .invoke(new PodcastScriptAgent.ScriptRequest("single topic", summaries));

        assertThat(result).isNotNull();
        assertThat(result.segments()).hasSize(1);
    }

    @Test
    public void testGenerateScriptModelFailureFallback() {
        var summaries = List.of(
                new PaperSummary("arxiv:1", "Test Paper", "A test summary.")
        );

        // Return invalid JSON to trigger onFailure
        scriptModel.fixedResponse("not valid json");

        var result = componentClient
                .forAgent()
                .inSession("test-session-3")
                .method(PodcastScriptAgent::generateScript)
                .invoke(new PodcastScriptAgent.ScriptRequest("test topic", summaries));

        assertThat(result).isNotNull();
        assertThat(result.topic()).isEqualTo("test topic");
        assertThat(result.segments()).isEmpty();
    }
}
