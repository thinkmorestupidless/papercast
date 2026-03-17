package com.example.domain;

import java.util.List;

public record PodcastScript(
    String topic,
    String introduction,
    List<PaperSegment> segments,
    String conclusion
) {
    public record PaperSegment(
        String paperId,
        String paperTitle,
        String narrative
    ) {}

    public static PodcastScript empty(String topic) {
        return new PodcastScript(topic, "", List.of(), "");
    }
}
