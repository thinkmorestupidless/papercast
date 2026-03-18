package com.example.domain;

import java.time.*;
import java.util.ArrayList;
import java.util.List;

public record PodcastSchedule(
        String scheduleId,
        String userId,
        String searchTerms,
        Cadence cadence,
        int timeHour,
        int timeMinute,
        DayOfWeek dayOfWeek,
        Integer dayOfMonth,
        Status status,
        Instant createdAt,
        Instant lastModifiedAt,
        Instant nextRunAt,
        List<ScheduleRun> recentRuns) {

    public enum Cadence { DAILY, WEEKLY, MONTHLY }
    public enum Status  { ACTIVE, PAUSED }

    public record ScheduleRun(
            String runId,
            Instant triggeredAt,
            String workflowId,
            String workflowStatusUrl
    ) {}

    public static PodcastSchedule create(
            String scheduleId,
            String userId,
            String searchTerms,
            Cadence cadence,
            int timeHour,
            int timeMinute,
            DayOfWeek dayOfWeek,
            Integer dayOfMonth,
            Instant now,
            Instant nextRunAt
    ) {
        return new PodcastSchedule(
                scheduleId, userId, searchTerms, cadence,
                timeHour, timeMinute, dayOfWeek, dayOfMonth,
                Status.ACTIVE, now, now, nextRunAt, List.of()
        );
    }

    public Instant nextRunAfter(Instant from) {
        ZonedDateTime zdt = from.atZone(ZoneOffset.UTC);
        return switch (cadence) {
            case DAILY   -> nextDailyAfter(zdt);
            case WEEKLY  -> nextWeeklyAfter(zdt);
            case MONTHLY -> nextMonthlyAfter(zdt);
        };
    }

    private Instant nextDailyAfter(ZonedDateTime from) {
        ZonedDateTime candidate = from.withHour(timeHour).withMinute(timeMinute)
                .withSecond(0).withNano(0);
        if (!candidate.isAfter(from)) {
            candidate = candidate.plusDays(1);
        }
        return candidate.toInstant();
    }

    private Instant nextWeeklyAfter(ZonedDateTime from) {
        ZonedDateTime candidate = from.with(dayOfWeek).withHour(timeHour).withMinute(timeMinute)
                .withSecond(0).withNano(0);
        if (!candidate.isAfter(from)) {
            candidate = candidate.plusWeeks(1);
        }
        return candidate.toInstant();
    }

    private Instant nextMonthlyAfter(ZonedDateTime from) {
        ZonedDateTime candidate = from.withDayOfMonth(1)
                .withHour(timeHour).withMinute(timeMinute).withSecond(0).withNano(0);
        for (int attempts = 0; attempts < 24; attempts++) {
            int maxDay = candidate.getMonth().length(Year.isLeap(candidate.getYear()));
            if (dayOfMonth <= maxDay) {
                ZonedDateTime target = candidate.withDayOfMonth(dayOfMonth);
                if (target.isAfter(from)) {
                    return target.toInstant();
                }
            }
            candidate = candidate.plusMonths(1);
        }
        return candidate.toInstant();
    }

    public PodcastSchedule withPaused() {
        return new PodcastSchedule(scheduleId, userId, searchTerms, cadence,
                timeHour, timeMinute, dayOfWeek, dayOfMonth,
                Status.PAUSED, createdAt, Instant.now(), null, recentRuns);
    }

    public PodcastSchedule withResumed(Instant nextRunAt) {
        return new PodcastSchedule(scheduleId, userId, searchTerms, cadence,
                timeHour, timeMinute, dayOfWeek, dayOfMonth,
                Status.ACTIVE, createdAt, Instant.now(), nextRunAt, recentRuns);
    }

    public PodcastSchedule withUpdated(
            String searchTerms,
            Cadence cadence,
            int timeHour,
            int timeMinute,
            DayOfWeek dayOfWeek,
            Integer dayOfMonth,
            Instant nextRunAt,
            Instant now
    ) {
        return new PodcastSchedule(scheduleId, userId, searchTerms, cadence,
                timeHour, timeMinute, dayOfWeek, dayOfMonth,
                status, createdAt, now, nextRunAt, recentRuns);
    }

    public PodcastSchedule withRunRecorded(ScheduleRun run, Instant nextRunAt) {
        List<ScheduleRun> updated = new ArrayList<>();
        updated.add(run);
        updated.addAll(recentRuns);
        if (updated.size() > 100) {
            updated = updated.subList(0, 100);
        }
        return new PodcastSchedule(scheduleId, userId, searchTerms, cadence,
                timeHour, timeMinute, dayOfWeek, dayOfMonth,
                status, createdAt, Instant.now(), nextRunAt, List.copyOf(updated));
    }
}
