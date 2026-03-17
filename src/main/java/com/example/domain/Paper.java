package com.example.domain;

import java.util.List;

public record Paper(
    String id,
    String title,
    List<String> authors,
    String publicationDate,
    Source source,
    String url,
    String abstractText
) {
    public enum Source {
        ARXIV, PUBMED, SEMANTIC_SCHOLAR
    }
}
