package com.example.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import com.example.domain.PodcastSchedule;

import java.time.DayOfWeek;
import java.time.Instant;

@Component(id = "podcast-schedule")
public class PodcastScheduleEntity extends KeyValueEntity<PodcastSchedule> {

    // --- Command / Response records ---

    public record CreateSchedule(
            String userId,
            String searchTerms,
            PodcastSchedule.Cadence cadence,
            int timeHour,
            int timeMinute,
            DayOfWeek dayOfWeek,
            Integer dayOfMonth,
            Instant nextRunAt
    ) {}

    public record ScheduleCreatedResponse(String scheduleId, Instant nextRunAt) {}

    public record ResumeSchedule(Instant nextRunAt) {}

    public record UpdateSchedule(
            String searchTerms,
            PodcastSchedule.Cadence cadence,
            int timeHour,
            int timeMinute,
            DayOfWeek dayOfWeek,
            Integer dayOfMonth,
            Instant nextRunAt
    ) {}

    public record RecordRun(
            String runId,
            Instant triggeredAt,
            String workflowId,
            String workflowStatusUrl,
            Instant nextRunAt
    ) {}

    // --- Command handlers ---

    public Effect<ScheduleCreatedResponse> createSchedule(CreateSchedule cmd) {
        if (currentState() != null) {
            return effects().error("Schedule already exists: " + commandContext().entityId());
        }
        if (cmd.searchTerms() == null || cmd.searchTerms().isBlank()) {
            return effects().error("searchTerms must not be blank");
        }
        if (cmd.timeHour() < 0 || cmd.timeHour() > 23) {
            return effects().error("timeHour must be 0-23");
        }
        if (cmd.timeMinute() < 0 || cmd.timeMinute() > 59) {
            return effects().error("timeMinute must be 0-59");
        }
        if (cmd.cadence() == PodcastSchedule.Cadence.WEEKLY && cmd.dayOfWeek() == null) {
            return effects().error("dayOfWeek is required for WEEKLY cadence");
        }
        if (cmd.cadence() == PodcastSchedule.Cadence.MONTHLY) {
            if (cmd.dayOfMonth() == null || cmd.dayOfMonth() < 1 || cmd.dayOfMonth() > 28) {
                return effects().error("dayOfMonth must be 1-28 for MONTHLY cadence");
            }
        }
        var schedule = PodcastSchedule.create(
                commandContext().entityId(),
                cmd.userId(), cmd.searchTerms(), cmd.cadence(),
                cmd.timeHour(), cmd.timeMinute(), cmd.dayOfWeek(), cmd.dayOfMonth(),
                Instant.now(), cmd.nextRunAt()
        );
        return effects().updateState(schedule)
                .thenReply(new ScheduleCreatedResponse(schedule.scheduleId(), schedule.nextRunAt()));
    }

    public Effect<Done> pauseSchedule() {
        if (currentState() == null) {
            return effects().error("Schedule not found");
        }
        if (currentState().status() == PodcastSchedule.Status.PAUSED) {
            return effects().error("Schedule is already paused");
        }
        return effects().updateState(currentState().withPaused())
                .thenReply(Done.getInstance());
    }

    public Effect<Done> resumeSchedule(ResumeSchedule cmd) {
        if (currentState() == null) {
            return effects().error("Schedule not found");
        }
        if (currentState().status() == PodcastSchedule.Status.ACTIVE) {
            return effects().error("Schedule is already active");
        }
        return effects().updateState(currentState().withResumed(cmd.nextRunAt()))
                .thenReply(Done.getInstance());
    }

    public Effect<Done> updateSchedule(UpdateSchedule cmd) {
        if (currentState() == null) {
            return effects().error("Schedule not found");
        }
        if (cmd.searchTerms() == null || cmd.searchTerms().isBlank()) {
            return effects().error("searchTerms must not be blank");
        }
        if (cmd.timeHour() < 0 || cmd.timeHour() > 23) {
            return effects().error("timeHour must be 0-23");
        }
        if (cmd.timeMinute() < 0 || cmd.timeMinute() > 59) {
            return effects().error("timeMinute must be 0-59");
        }
        if (cmd.cadence() == PodcastSchedule.Cadence.WEEKLY && cmd.dayOfWeek() == null) {
            return effects().error("dayOfWeek is required for WEEKLY cadence");
        }
        if (cmd.cadence() == PodcastSchedule.Cadence.MONTHLY) {
            if (cmd.dayOfMonth() == null || cmd.dayOfMonth() < 1 || cmd.dayOfMonth() > 28) {
                return effects().error("dayOfMonth must be 1-28 for MONTHLY cadence");
            }
        }
        return effects().updateState(currentState().withUpdated(
                cmd.searchTerms(), cmd.cadence(), cmd.timeHour(), cmd.timeMinute(),
                cmd.dayOfWeek(), cmd.dayOfMonth(), cmd.nextRunAt(), Instant.now()))
                .thenReply(Done.getInstance());
    }

    public Effect<Done> deleteSchedule() {
        if (currentState() == null) {
            return effects().reply(Done.getInstance());
        }
        return effects().deleteEntity().thenReply(Done.getInstance());
    }

    public Effect<Done> recordRun(RecordRun cmd) {
        if (currentState() == null) {
            return effects().reply(Done.getInstance());
        }
        var run = new PodcastSchedule.ScheduleRun(
                cmd.runId(), cmd.triggeredAt(), cmd.workflowId(), cmd.workflowStatusUrl());
        return effects().updateState(currentState().withRunRecorded(run, cmd.nextRunAt()))
                .thenReply(Done.getInstance());
    }

    public Effect<PodcastSchedule> getSchedule() {
        if (currentState() == null) {
            return effects().error("Schedule not found");
        }
        return effects().reply(currentState());
    }
}
