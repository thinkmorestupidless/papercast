package com.example.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.timedaction.TimedAction;
import akka.javasdk.timer.TimerScheduler;
import com.example.domain.PodcastSchedule;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Component(id = "podcast-schedule-trigger")
public class PodcastScheduleTrigger extends TimedAction {

    private final ComponentClient componentClient;
    private final TimerScheduler timerScheduler;

    public PodcastScheduleTrigger(ComponentClient componentClient, TimerScheduler timerScheduler) {
        this.componentClient = componentClient;
        this.timerScheduler = timerScheduler;
    }

    public Effect trigger(String scheduleId) {
        // 1. Fetch schedule state — if gone or PAUSED, end the timer chain
        PodcastSchedule schedule;
        try {
            schedule = componentClient.forKeyValueEntity(scheduleId)
                    .method(PodcastScheduleEntity::getSchedule)
                    .invoke();
        } catch (Exception e) {
            // Entity deleted or error fetching — stop the chain
            return effects().done();
        }

        if (schedule == null || schedule.status() == PodcastSchedule.Status.PAUSED) {
            return effects().done();
        }

        // 2. Start a new podcast creation workflow
        String workflowId = UUID.randomUUID().toString();
        try {
            componentClient.forWorkflow(workflowId)
                    .method(PodcastCreationWorkflow::create)
                    .invoke(new PodcastCreationWorkflow.CreateCommand(schedule.searchTerms()));
        } catch (Exception e) {
            // Transient failure starting workflow — retry by returning error
            return effects().error("Failed to start PodcastCreationWorkflow: " + e.getMessage());
        }

        // 3. Record the run and advance nextRunAt
        Instant now = Instant.now();
        Instant nextRunAt = schedule.nextRunAfter(now);
        String runId = UUID.randomUUID().toString();
        try {
            componentClient.forKeyValueEntity(scheduleId)
                    .method(PodcastScheduleEntity::recordRun)
                    .invoke(new PodcastScheduleEntity.RecordRun(
                            runId, now, workflowId, "/podcast/" + workflowId + "/status", nextRunAt));
        } catch (Exception e) {
            // Non-critical — still reschedule even if record fails
        }

        // 4. Schedule the next timer
        Duration delay = Duration.between(now, nextRunAt);
        timerScheduler.createSingleTimer(
                "podcast-schedule-" + scheduleId,
                delay,
                componentClient.forTimedAction()
                        .method(PodcastScheduleTrigger::trigger)
                        .deferred(scheduleId)
        );

        return effects().done();
    }
}
