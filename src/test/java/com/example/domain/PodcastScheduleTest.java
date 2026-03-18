package com.example.domain;

import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class PodcastScheduleTest {

    private static PodcastSchedule daily(int hour, int minute) {
        return PodcastSchedule.create("s1", "u1", "AI news",
                PodcastSchedule.Cadence.DAILY, hour, minute, null, null,
                Instant.now(), Instant.now());
    }

    private static PodcastSchedule weekly(DayOfWeek dow, int hour, int minute) {
        return PodcastSchedule.create("s1", "u1", "AI news",
                PodcastSchedule.Cadence.WEEKLY, hour, minute, dow, null,
                Instant.now(), Instant.now());
    }

    private static PodcastSchedule monthly(int day, int hour, int minute) {
        return PodcastSchedule.create("s1", "u1", "AI news",
                PodcastSchedule.Cadence.MONTHLY, hour, minute, null, day,
                Instant.now(), Instant.now());
    }

    @Test
    public void testNextDailyAfterSameDayFutureTime() {
        var schedule = daily(10, 0);
        Instant from = LocalDateTime.of(2026, 3, 18, 9, 0).toInstant(ZoneOffset.UTC);
        Instant next = schedule.nextRunAfter(from);
        assertThat(next).isEqualTo(LocalDateTime.of(2026, 3, 18, 10, 0).toInstant(ZoneOffset.UTC));
    }

    @Test
    public void testNextDailyRollsOverToNextDay() {
        var schedule = daily(10, 0);
        Instant from = LocalDateTime.of(2026, 3, 18, 11, 0).toInstant(ZoneOffset.UTC);
        Instant next = schedule.nextRunAfter(from);
        assertThat(next).isEqualTo(LocalDateTime.of(2026, 3, 19, 10, 0).toInstant(ZoneOffset.UTC));
    }

    @Test
    public void testNextWeeklySameWeekFuture() {
        var schedule = weekly(DayOfWeek.FRIDAY, 15, 0);
        Instant from = LocalDateTime.of(2026, 3, 16, 9, 0).toInstant(ZoneOffset.UTC); // Monday
        Instant next = schedule.nextRunAfter(from);
        assertThat(next).isEqualTo(LocalDateTime.of(2026, 3, 20, 15, 0).toInstant(ZoneOffset.UTC)); // Friday
    }

    @Test
    public void testNextWeeklyRollsToNextWeek() {
        var schedule = weekly(DayOfWeek.FRIDAY, 15, 0);
        Instant from = LocalDateTime.of(2026, 3, 21, 9, 0).toInstant(ZoneOffset.UTC); // Saturday
        Instant next = schedule.nextRunAfter(from);
        assertThat(next).isEqualTo(LocalDateTime.of(2026, 3, 27, 15, 0).toInstant(ZoneOffset.UTC)); // Next Friday
    }

    @Test
    public void testNextMonthlySameMonthFuture() {
        var schedule = monthly(25, 10, 0);
        Instant from = LocalDateTime.of(2026, 3, 18, 10, 0).toInstant(ZoneOffset.UTC);
        Instant next = schedule.nextRunAfter(from);
        assertThat(next).isEqualTo(LocalDateTime.of(2026, 3, 25, 10, 0).toInstant(ZoneOffset.UTC));
    }

    @Test
    public void testNextMonthlyRollsToNextMonth() {
        var schedule = monthly(15, 10, 0);
        Instant from = LocalDateTime.of(2026, 3, 20, 10, 0).toInstant(ZoneOffset.UTC);
        Instant next = schedule.nextRunAfter(from);
        assertThat(next).isEqualTo(LocalDateTime.of(2026, 4, 15, 10, 0).toInstant(ZoneOffset.UTC));
    }

    @Test
    public void testNextMonthlySkipsShortMonth() {
        // dayOfMonth=28, from=January 29 → February 28
        var schedule = monthly(28, 10, 0);
        Instant from = LocalDateTime.of(2026, 1, 29, 10, 0).toInstant(ZoneOffset.UTC);
        Instant next = schedule.nextRunAfter(from);
        assertThat(next).isEqualTo(LocalDateTime.of(2026, 2, 28, 10, 0).toInstant(ZoneOffset.UTC));
    }

    @Test
    public void testWithRunRecordedCapsAt100() {
        var schedule = daily(10, 0);
        List<PodcastSchedule.ScheduleRun> runs = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            runs.add(new PodcastSchedule.ScheduleRun("run-" + i, Instant.now(), "wf-" + i, "/podcast/wf-" + i + "/status"));
        }
        var fullSchedule = new PodcastSchedule(
                schedule.scheduleId(), schedule.userId(), schedule.searchTerms(), schedule.cadence(),
                schedule.timeHour(), schedule.timeMinute(), schedule.dayOfWeek(), schedule.dayOfMonth(),
                schedule.status(), schedule.createdAt(), schedule.lastModifiedAt(), schedule.nextRunAt(),
                List.copyOf(runs));
        var newRun = new PodcastSchedule.ScheduleRun("run-new", Instant.now(), "wf-new", "/podcast/wf-new/status");
        var updated = fullSchedule.withRunRecorded(newRun, Instant.now());
        assertThat(updated.recentRuns()).hasSize(100);
        assertThat(updated.recentRuns().get(0).runId()).isEqualTo("run-new");
        assertThat(updated.recentRuns().stream().noneMatch(r -> r.runId().equals("run-99"))).isTrue();
    }
}
