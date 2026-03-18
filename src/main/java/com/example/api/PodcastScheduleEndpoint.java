package com.example.api;

import akka.Done;
import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.JWT;
import akka.javasdk.annotations.http.*;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpException;
import akka.javasdk.http.HttpResponses;
import akka.javasdk.timer.TimerScheduler;
import com.example.application.PodcastScheduleEntity;
import com.example.application.SchedulesByOwnerView;
import com.example.domain.PodcastSchedule;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@HttpEndpoint("/schedules")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@JWT(validate = JWT.JwtMethodMode.BEARER_TOKEN)
public class PodcastScheduleEndpoint extends AbstractHttpEndpoint {

    // --- Request / Response records ---

    public record CreateScheduleRequest(
            String searchTerms,
            String cadence,
            int timeHour,
            int timeMinute,
            String dayOfWeek,
            Integer dayOfMonth
    ) {}

    public record UpdateScheduleRequest(
            String searchTerms,
            String cadence,
            int timeHour,
            int timeMinute,
            String dayOfWeek,
            Integer dayOfMonth
    ) {}

    public record RunsResponse(List<PodcastSchedule.ScheduleRun> runs) {}

    // --- Fields ---

    private final ComponentClient componentClient;
    private final TimerScheduler timerScheduler;

    public PodcastScheduleEndpoint(ComponentClient componentClient, TimerScheduler timerScheduler) {
        this.componentClient = componentClient;
        this.timerScheduler = timerScheduler;
    }

    // --- Helpers ---

    private String extractUserId() {
        return requestContext().getJwtClaims().subject()
                .orElseThrow(() -> HttpException.badRequest("Missing sub claim"));
    }

    private static String timerName(String scheduleId) {
        return "podcast-schedule-" + scheduleId;
    }

    private static PodcastSchedule.Cadence parseCadence(String cadence) {
        try {
            return PodcastSchedule.Cadence.valueOf(cadence.toUpperCase());
        } catch (Exception e) {
            throw HttpException.badRequest("Invalid cadence: " + cadence + ". Must be DAILY, WEEKLY, or MONTHLY");
        }
    }

    private static DayOfWeek parseDayOfWeek(String dayOfWeek) {
        try {
            return DayOfWeek.valueOf(dayOfWeek.toUpperCase());
        } catch (Exception e) {
            throw HttpException.badRequest("Invalid dayOfWeek: " + dayOfWeek);
        }
    }

    private void validateScheduleRequest(String searchTerms, PodcastSchedule.Cadence cadence,
            int timeHour, int timeMinute, String dayOfWeekStr, Integer dayOfMonth) {
        if (searchTerms == null || searchTerms.isBlank()) {
            throw HttpException.badRequest("searchTerms must not be blank");
        }
        if (timeHour < 0 || timeHour > 23) {
            throw HttpException.badRequest("timeHour must be 0-23");
        }
        if (timeMinute < 0 || timeMinute > 59) {
            throw HttpException.badRequest("timeMinute must be 0-59");
        }
        if (cadence == PodcastSchedule.Cadence.WEEKLY && (dayOfWeekStr == null || dayOfWeekStr.isBlank())) {
            throw HttpException.badRequest("dayOfWeek is required for WEEKLY cadence");
        }
        if (cadence == PodcastSchedule.Cadence.MONTHLY) {
            if (dayOfMonth == null || dayOfMonth < 1 || dayOfMonth > 28) {
                throw HttpException.badRequest("dayOfMonth must be 1-28 for MONTHLY cadence");
            }
        }
    }

    private Instant computeNextRunAt(PodcastSchedule.Cadence cadence, int timeHour, int timeMinute,
            DayOfWeek dayOfWeek, Integer dayOfMonth) {
        var tmp = PodcastSchedule.create(
                "tmp", "tmp", "tmp", cadence,
                timeHour, timeMinute, dayOfWeek, dayOfMonth,
                Instant.now(), Instant.now()
        );
        return tmp.nextRunAfter(Instant.now());
    }

    private PodcastSchedule fetchOwnSchedule(String scheduleId, String userId) {
        PodcastSchedule state;
        try {
            state = componentClient.forKeyValueEntity(scheduleId)
                    .method(PodcastScheduleEntity::getSchedule)
                    .invoke();
        } catch (Exception e) {
            throw HttpException.notFound();
        }
        if (!state.userId().equals(userId)) {
            throw HttpException.notFound();
        }
        return state;
    }

    // --- POST /schedules ---

    @Post
    public HttpResponse createSchedule(CreateScheduleRequest req) {
        String userId = extractUserId();
        PodcastSchedule.Cadence cadence = parseCadence(req.cadence());
        validateScheduleRequest(req.searchTerms(), cadence, req.timeHour(), req.timeMinute(),
                req.dayOfWeek(), req.dayOfMonth());

        DayOfWeek dayOfWeek = (req.dayOfWeek() != null && !req.dayOfWeek().isBlank())
                ? parseDayOfWeek(req.dayOfWeek()) : null;

        // Check 10-schedule limit
        var existing = componentClient.forView()
                .method(SchedulesByOwnerView::getByOwner)
                .invoke(userId);
        if (existing.schedules().size() >= 10) {
            throw HttpException.badRequest("Maximum of 10 schedules per user reached");
        }

        String scheduleId = UUID.randomUUID().toString();
        Instant nextRunAt = computeNextRunAt(cadence, req.timeHour(), req.timeMinute(),
                dayOfWeek, req.dayOfMonth());

        var response = componentClient.forKeyValueEntity(scheduleId)
                .method(PodcastScheduleEntity::createSchedule)
                .invoke(new PodcastScheduleEntity.CreateSchedule(
                        userId, req.searchTerms(), cadence,
                        req.timeHour(), req.timeMinute(), dayOfWeek, req.dayOfMonth(), nextRunAt));

        Duration delay = Duration.between(Instant.now(), nextRunAt);
        timerScheduler.createSingleTimer(
                timerName(scheduleId),
                delay,
                componentClient.forTimedAction()
                        .method(com.example.application.PodcastScheduleTrigger::trigger)
                        .deferred(scheduleId)
        );

        return HttpResponses.created(response);
    }

    // --- GET /schedules ---

    @Get
    public SchedulesByOwnerView.ScheduleSummaries listSchedules() {
        String userId = extractUserId();
        return componentClient.forView()
                .method(SchedulesByOwnerView::getByOwner)
                .invoke(userId);
    }

    // --- GET /schedules/{id} ---

    @Get("/{id}")
    public PodcastSchedule getSchedule(String id) {
        String userId = extractUserId();
        return fetchOwnSchedule(id, userId);
    }

    // --- PATCH /schedules/{id}/pause ---

    @Patch("/{id}/pause")
    public HttpResponse pauseSchedule(String id) {
        String userId = extractUserId();
        fetchOwnSchedule(id, userId); // ownership check
        try {
            timerScheduler.delete(timerName(id));
        } catch (Exception ignored) {}
        try {
            componentClient.forKeyValueEntity(id)
                    .method(PodcastScheduleEntity::pauseSchedule)
                    .invoke();
        } catch (Exception e) {
            throw HttpException.badRequest(e.getMessage());
        }
        return HttpResponses.ok();
    }

    // --- PATCH /schedules/{id}/resume ---

    @Patch("/{id}/resume")
    public HttpResponse resumeSchedule(String id) {
        String userId = extractUserId();
        PodcastSchedule state = fetchOwnSchedule(id, userId);
        Instant nextRunAt = state.nextRunAfter(Instant.now());
        try {
            componentClient.forKeyValueEntity(id)
                    .method(PodcastScheduleEntity::resumeSchedule)
                    .invoke(new PodcastScheduleEntity.ResumeSchedule(nextRunAt));
        } catch (Exception e) {
            throw HttpException.badRequest(e.getMessage());
        }
        Duration delay = Duration.between(Instant.now(), nextRunAt);
        timerScheduler.createSingleTimer(
                timerName(id),
                delay,
                componentClient.forTimedAction()
                        .method(com.example.application.PodcastScheduleTrigger::trigger)
                        .deferred(id)
        );
        return HttpResponses.ok();
    }

    // --- PUT /schedules/{id} ---

    @Put("/{id}")
    public HttpResponse updateSchedule(String id, UpdateScheduleRequest req) {
        String userId = extractUserId();
        fetchOwnSchedule(id, userId); // ownership check
        PodcastSchedule.Cadence cadence = parseCadence(req.cadence());
        validateScheduleRequest(req.searchTerms(), cadence, req.timeHour(), req.timeMinute(),
                req.dayOfWeek(), req.dayOfMonth());
        DayOfWeek dayOfWeek = (req.dayOfWeek() != null && !req.dayOfWeek().isBlank())
                ? parseDayOfWeek(req.dayOfWeek()) : null;
        Instant nextRunAt = computeNextRunAt(cadence, req.timeHour(), req.timeMinute(),
                dayOfWeek, req.dayOfMonth());
        try {
            componentClient.forKeyValueEntity(id)
                    .method(PodcastScheduleEntity::updateSchedule)
                    .invoke(new PodcastScheduleEntity.UpdateSchedule(
                            req.searchTerms(), cadence, req.timeHour(), req.timeMinute(),
                            dayOfWeek, req.dayOfMonth(), nextRunAt));
        } catch (Exception e) {
            throw HttpException.badRequest(e.getMessage());
        }
        try {
            timerScheduler.delete(timerName(id));
        } catch (Exception ignored) {}
        Duration delay = Duration.between(Instant.now(), nextRunAt);
        timerScheduler.createSingleTimer(
                timerName(id),
                delay,
                componentClient.forTimedAction()
                        .method(com.example.application.PodcastScheduleTrigger::trigger)
                        .deferred(id)
        );
        return HttpResponses.ok();
    }

    // --- DELETE /schedules/{id} ---

    @Delete("/{id}")
    public HttpResponse deleteSchedule(String id) {
        String userId = extractUserId();
        fetchOwnSchedule(id, userId); // ownership check
        try {
            timerScheduler.delete(timerName(id));
        } catch (Exception ignored) {}
        componentClient.forKeyValueEntity(id)
                .method(PodcastScheduleEntity::deleteSchedule)
                .invoke();
        return HttpResponses.ok();
    }

    // --- GET /schedules/{id}/runs ---

    @Get("/{id}/runs")
    public RunsResponse getScheduleRuns(String id) {
        String userId = extractUserId();
        PodcastSchedule state = fetchOwnSchedule(id, userId);
        return new RunsResponse(state.recentRuns());
    }
}
