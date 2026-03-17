package com.example.domain;

import java.util.List;

public record PodcastCreationState(
    String query,
    List<Paper> papers,
    List<PaperSummary> summaries,
    PodcastScript script,
    Status status,
    String errorMessage
) {
    public enum Status {
        CREATED, SEARCHING, SUMMARISING, SCRIPTING, COMPLETE, FAILED
    }

    public static PodcastCreationState initial(String query) {
        return new PodcastCreationState(query, List.of(), List.of(), null, Status.CREATED, null);
    }

    public PodcastCreationState withSearching() {
        return new PodcastCreationState(query, List.of(), List.of(), null, Status.SEARCHING, null);
    }

    public PodcastCreationState withPapers(List<Paper> papers) {
        return new PodcastCreationState(query, papers, List.of(), null, Status.SUMMARISING, null);
    }

    public PodcastCreationState withSummaries(List<PaperSummary> summaries) {
        return new PodcastCreationState(query, papers, summaries, null, Status.SCRIPTING, null);
    }

    public PodcastCreationState withScript(PodcastScript script) {
        return new PodcastCreationState(query, papers, summaries, script, Status.COMPLETE, null);
    }

    public PodcastCreationState withError(String message) {
        return new PodcastCreationState(query, papers, summaries, script, Status.FAILED, message);
    }
}
