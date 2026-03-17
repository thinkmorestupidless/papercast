package com.example.application;

import akka.javasdk.JsonSupport;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import com.example.domain.Paper;
import com.example.domain.PaperSummary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class PaperSummaryAgentIntegrationTest extends TestKitSupport {

    private final TestModelProvider agentModel = new TestModelProvider();

    @Override
    protected TestKit.Settings testKitSettings() {
        return TestKit.Settings.DEFAULT
                .withModelProvider(PaperSummaryAgent.class, agentModel);
    }

    @Test
    public void testSummariseHappyPath() {
        var papers = List.of(
                new Paper("arxiv:1", "Quantum Entanglement", List.of("Alice"), "2024-01", Paper.Source.ARXIV, "https://arxiv.org/abs/1", "Abstract about quantum entanglement"),
                new Paper("arxiv:2", "Black Holes", List.of("Bob"), "2024-02", Paper.Source.ARXIV, "https://arxiv.org/abs/2", "Abstract about black holes"),
                new Paper("pmid:3", "Dark Matter", List.of("Carol"), "2024-03", Paper.Source.PUBMED, "https://pubmed.ncbi.nlm.nih.gov/3/", "Abstract about dark matter")
        );

        var expectedResponse = new PaperSummaryAgent.SummariseResponse(List.of(
                new PaperSummary("arxiv:1", "Quantum Entanglement", "A summary of quantum entanglement."),
                new PaperSummary("arxiv:2", "Black Holes", "A summary of black holes."),
                new PaperSummary("pmid:3", "Dark Matter", "A summary of dark matter.")
        ));
        agentModel.fixedResponse(JsonSupport.encodeToString(expectedResponse));

        var result = componentClient
                .forAgent()
                .inSession("test-session-1")
                .method(PaperSummaryAgent::summarise)
                .invoke(new PaperSummaryAgent.SummariseRequest(papers));

        assertThat(result.summaries()).hasSize(3);
        assertThat(result.summaries().get(0).paperId()).isEqualTo("arxiv:1");
    }

    @Test
    public void testSummariseEmptyPaperList() {
        var result = componentClient
                .forAgent()
                .inSession("test-session-2")
                .method(PaperSummaryAgent::summarise)
                .invoke(new PaperSummaryAgent.SummariseRequest(List.of()));

        assertThat(result.summaries()).isEmpty();
    }

    @Test
    public void testSummariseModelFailureFallback() {
        var papers = List.of(
                new Paper("arxiv:1", "Test Paper", List.of("Author"), "2024-01", Paper.Source.ARXIV, "https://arxiv.org/abs/1", "Abstract")
        );

        // Return invalid JSON to trigger onFailure
        agentModel.fixedResponse("not valid json at all");

        var result = componentClient
                .forAgent()
                .inSession("test-session-3")
                .method(PaperSummaryAgent::summarise)
                .invoke(new PaperSummaryAgent.SummariseRequest(papers));

        assertThat(result.summaries()).isEmpty();
    }
}
