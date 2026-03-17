package com.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.AgentRole;
import akka.javasdk.annotations.Component;
import com.example.domain.PaperSummary;
import com.example.domain.PodcastScript;

import java.util.List;

@Component(id = "podcast-script")
@AgentRole("Generates an engaging podcast script from a collection of paper summaries")
public class PodcastScriptAgent extends Agent {

    public record ScriptRequest(String topic, List<PaperSummary> summaries) {}

    private static final String SYSTEM_MESSAGE = """
            You are an experienced science podcast writer and host.
            You create engaging, conversational podcast scripts that make scientific research accessible and interesting.
            Your scripts should:
            - Open with a warm, captivating introduction that contextualises the research topic
            - Dedicate one narrative segment per paper, weaving the findings into a compelling story
            - Use natural conversational language as if speaking directly to a curious listener
            - Close with a thoughtful reflection on what the research collectively reveals
            Return a structured podcast script as JSON matching the required format.
            """;

    public Effect<PodcastScript> generateScript(ScriptRequest request) {
        var summaryText = new StringBuilder();
        for (var summary : request.summaries()) {
            summaryText.append("Paper: ").append(summary.paperTitle()).append("\n");
            summaryText.append("ID: ").append(summary.paperId()).append("\n");
            summaryText.append("Summary: ").append(summary.summary()).append("\n\n");
        }

        return effects()
                .systemMessage(SYSTEM_MESSAGE)
                .userMessage("Create a podcast script for the topic: \"" + request.topic() + "\"\n\n"
                        + "Using these " + request.summaries().size() + " paper summaries:\n\n" + summaryText)
                .responseConformsTo(PodcastScript.class)
                .onFailure(t -> PodcastScript.empty(request.topic()))
                .thenReply();
    }
}
