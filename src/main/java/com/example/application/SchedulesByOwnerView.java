package com.example.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.DeleteHandler;
import akka.javasdk.annotations.Query;
import akka.javasdk.annotations.Table;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import com.example.domain.PodcastSchedule;

import java.time.Instant;
import java.util.List;

@Component(id = "schedules-by-owner-view")
public class SchedulesByOwnerView extends View {

    public record ScheduleSummary(
            String scheduleId,
            String userId,
            String searchTerms,
            String cadence,
            String status,
            Instant nextRunAt,
            Instant createdAt
    ) {}

    public record ScheduleSummaries(List<ScheduleSummary> schedules) {}

    @Consume.FromKeyValueEntity(PodcastScheduleEntity.class)
    @Table("schedule_rows")
    public static class ScheduleRows extends TableUpdater<ScheduleSummary> {
        public Effect<ScheduleSummary> onUpdate(PodcastSchedule state) {
            // nextRunAt is null when a schedule is paused; use Instant.EPOCH as sentinel
            // to avoid null timestamp storage issues in the view pipeline
            Instant nextRunAt = state.nextRunAt() != null ? state.nextRunAt() : Instant.EPOCH;
            return effects().updateRow(new ScheduleSummary(
                    state.scheduleId(),
                    state.userId(),
                    state.searchTerms(),
                    state.cadence().name(),
                    state.status().name(),
                    nextRunAt,
                    state.createdAt()
            ));
        }

        @DeleteHandler
        public Effect<ScheduleSummary> onDelete() {
            return effects().deleteRow();
        }
    }

    @Query("SELECT * AS schedules FROM schedule_rows WHERE userId = :userId")
    public QueryEffect<ScheduleSummaries> getByOwner(String userId) {
        return queryResult();
    }
}
