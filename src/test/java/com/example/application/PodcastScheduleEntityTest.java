package com.example.application;

import akka.javasdk.testkit.KeyValueEntityTestKit;
import com.example.domain.PodcastSchedule;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class PodcastScheduleEntityTest {

    private static final String SCHEDULE_ID = UUID.randomUUID().toString();
    private static final String USER_ID = "auth0|test-user-123";

    private KeyValueEntityTestKit<PodcastSchedule, PodcastScheduleEntity> newTestKit() {
        return KeyValueEntityTestKit.of(SCHEDULE_ID, PodcastScheduleEntity::new);
    }

    private PodcastScheduleEntity.CreateSchedule dailyCmd() {
        return new PodcastScheduleEntity.CreateSchedule(
                USER_ID, "machine learning",
                PodcastSchedule.Cadence.DAILY, 10, 0,
                null, null,
                Instant.now().plusSeconds(3600)
        );
    }

    @Test
    public void testCreateScheduleOnNewEntityCreatesActiveSchedule() {
        var testKit = newTestKit();
        var result = testKit.method(PodcastScheduleEntity::createSchedule).invoke(dailyCmd());
        assertThat(result.isReply()).isTrue();
        assertThat(result.getReply().scheduleId()).isEqualTo(SCHEDULE_ID);
        assertThat(result.getReply().nextRunAt()).isNotNull();

        var state = testKit.getState();
        assertThat(state).isNotNull();
        assertThat(state.status()).isEqualTo(PodcastSchedule.Status.ACTIVE);
        assertThat(state.searchTerms()).isEqualTo("machine learning");
        assertThat(state.userId()).isEqualTo(USER_ID);
    }

    @Test
    public void testCreateScheduleOnExistingEntityReturnsError() {
        var testKit = newTestKit();
        testKit.method(PodcastScheduleEntity::createSchedule).invoke(dailyCmd());
        var result = testKit.method(PodcastScheduleEntity::createSchedule).invoke(dailyCmd());
        assertThat(result.isError()).isTrue();
    }

    @Test
    public void testPauseActiveScheduleSetsStatusPaused() {
        var testKit = newTestKit();
        testKit.method(PodcastScheduleEntity::createSchedule).invoke(dailyCmd());
        var result = testKit.method(PodcastScheduleEntity::pauseSchedule).invoke();
        assertThat(result.isReply()).isTrue();

        var state = testKit.getState();
        assertThat(state.status()).isEqualTo(PodcastSchedule.Status.PAUSED);
        assertThat(state.nextRunAt()).isNull();
    }

    @Test
    public void testPausePausedScheduleReturnsError() {
        var testKit = newTestKit();
        testKit.method(PodcastScheduleEntity::createSchedule).invoke(dailyCmd());
        testKit.method(PodcastScheduleEntity::pauseSchedule).invoke();
        var result = testKit.method(PodcastScheduleEntity::pauseSchedule).invoke();
        assertThat(result.isError()).isTrue();
    }

    @Test
    public void testResumePausedScheduleSetsStatusActive() {
        var testKit = newTestKit();
        testKit.method(PodcastScheduleEntity::createSchedule).invoke(dailyCmd());
        testKit.method(PodcastScheduleEntity::pauseSchedule).invoke();
        var nextRunAt = Instant.now().plusSeconds(7200);
        var result = testKit.method(PodcastScheduleEntity::resumeSchedule)
                .invoke(new PodcastScheduleEntity.ResumeSchedule(nextRunAt));
        assertThat(result.isReply()).isTrue();

        var state = testKit.getState();
        assertThat(state.status()).isEqualTo(PodcastSchedule.Status.ACTIVE);
        assertThat(state.nextRunAt()).isEqualTo(nextRunAt);
    }

    @Test
    public void testResumeActiveScheduleReturnsError() {
        var testKit = newTestKit();
        testKit.method(PodcastScheduleEntity::createSchedule).invoke(dailyCmd());
        var result = testKit.method(PodcastScheduleEntity::resumeSchedule)
                .invoke(new PodcastScheduleEntity.ResumeSchedule(Instant.now().plusSeconds(3600)));
        assertThat(result.isError()).isTrue();
    }

    @Test
    public void testUpdateScheduleChangesFields() {
        var testKit = newTestKit();
        testKit.method(PodcastScheduleEntity::createSchedule).invoke(dailyCmd());
        var updateCmd = new PodcastScheduleEntity.UpdateSchedule(
                "quantum computing",
                PodcastSchedule.Cadence.WEEKLY, 15, 30,
                DayOfWeek.WEDNESDAY, null,
                Instant.now().plusSeconds(3600)
        );
        var result = testKit.method(PodcastScheduleEntity::updateSchedule).invoke(updateCmd);
        assertThat(result.isReply()).isTrue();

        var state = testKit.getState();
        assertThat(state.searchTerms()).isEqualTo("quantum computing");
        assertThat(state.cadence()).isEqualTo(PodcastSchedule.Cadence.WEEKLY);
        assertThat(state.dayOfWeek()).isEqualTo(DayOfWeek.WEDNESDAY);
    }

    @Test
    public void testUpdateNonExistentScheduleReturnsError() {
        var testKit = newTestKit();
        var updateCmd = new PodcastScheduleEntity.UpdateSchedule(
                "quantum computing", PodcastSchedule.Cadence.DAILY, 10, 0,
                null, null, Instant.now().plusSeconds(3600)
        );
        var result = testKit.method(PodcastScheduleEntity::updateSchedule).invoke(updateCmd);
        assertThat(result.isError()).isTrue();
    }

    @Test
    public void testDeleteScheduleClearsState() {
        var testKit = newTestKit();
        testKit.method(PodcastScheduleEntity::createSchedule).invoke(dailyCmd());
        var result = testKit.method(PodcastScheduleEntity::deleteSchedule).invoke();
        assertThat(result.isReply()).isTrue();
        assertThat(testKit.getState()).isNull();
    }

    @Test
    public void testRecordRunAppendsToRecentRuns() {
        var testKit = newTestKit();
        testKit.method(PodcastScheduleEntity::createSchedule).invoke(dailyCmd());
        var runCmd = new PodcastScheduleEntity.RecordRun(
                UUID.randomUUID().toString(), Instant.now(),
                UUID.randomUUID().toString(), "/podcast/wf-123/status",
                Instant.now().plusSeconds(86400)
        );
        var result = testKit.method(PodcastScheduleEntity::recordRun).invoke(runCmd);
        assertThat(result.isReply()).isTrue();

        var state = testKit.getState();
        assertThat(state.recentRuns()).hasSize(1);
        assertThat(state.recentRuns().get(0).runId()).isEqualTo(runCmd.runId());
    }

    @Test
    public void testRecordRunOnNullStateIsNoOp() {
        var testKit = newTestKit();
        var runCmd = new PodcastScheduleEntity.RecordRun(
                UUID.randomUUID().toString(), Instant.now(),
                UUID.randomUUID().toString(), "/podcast/wf-123/status",
                Instant.now().plusSeconds(86400)
        );
        var result = testKit.method(PodcastScheduleEntity::recordRun).invoke(runCmd);
        assertThat(result.isReply()).isTrue();
        assertThat(testKit.getState()).isNull();
    }

    @Test
    public void testGetScheduleOnNonExistentReturnsError() {
        var testKit = newTestKit();
        var result = testKit.method(PodcastScheduleEntity::getSchedule).invoke();
        assertThat(result.isError()).isTrue();
    }
}
