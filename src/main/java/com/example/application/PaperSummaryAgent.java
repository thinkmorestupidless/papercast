package com.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.AgentRole;
import akka.javasdk.annotations.Component;
import com.example.domain.Paper;
import com.example.domain.PaperSummary;

import java.util.List;

@Component(id = "paper-summary")
@AgentRole("Summarises scientific papers into 2-4 sentence plain-language overviews")
public class PaperSummaryAgent extends Agent {

    public record SummariseRequest(List<Paper> papers) {}

    public record SummariseResponse(List<PaperSummary> summaries) {}

    private static final String SYSTEM_MESSAGE = """
            You are a science communicator who excels at making complex research accessible to general audiences.
            You will receive a list of scientific papers, each with a title and abstract.
            For each paper, write a 2-4 sentence plain-language summary that:
            - Explains the main finding or contribution in simple terms
            - Avoids jargon and technical terminology
            - Captures why the research matters
            Return the summaries in the same order as the input papers.
            """;

    public Effect<SummariseResponse> summarise(SummariseRequest request) {
        if (request.papers().isEmpty()) {
            return effects().reply(new SummariseResponse(List.of()));
        }

        var paperDescriptions = new StringBuilder();
        for (int i = 0; i < request.papers().size(); i++) {
            var paper = request.papers().get(i);
            paperDescriptions.append("Paper ").append(i + 1).append(":\n");
            paperDescriptions.append("ID: ").append(paper.id()).append("\n");
            paperDescriptions.append("Title: ").append(paper.title()).append("\n");
            if (paper.abstractText() != null) {
                paperDescriptions.append("Abstract: ").append(paper.abstractText()).append("\n");
            } else {
                paperDescriptions.append("Abstract: Not available\n");
            }
            paperDescriptions.append("\n");
        }

        return effects()
                .systemMessage(SYSTEM_MESSAGE)
                .userMessage("Please summarise the following " + request.papers().size() + " papers:\n\n" + paperDescriptions)
                .responseConformsTo(SummariseResponse.class)
                .onFailure(t -> new SummariseResponse(List.of()))
                .thenReply();
    }
}
