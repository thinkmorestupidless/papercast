package com.example.api;

import akka.javasdk.JsonSupport;
import akka.javasdk.testkit.TestKitSupport;
import com.example.application.PodcastScheduleEntity;
import com.example.application.SchedulesByOwnerView;
import com.example.domain.PodcastSchedule;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class PodcastScheduleEndpointIntegrationTest extends TestKitSupport {

    private static final String USER_A_ID = "auth0|sched-a-" + UUID.randomUUID();
    private static final String USER_B_ID = "auth0|sched-b-" + UUID.randomUUID();

    private String bearerTokenWith(Map<String, Object> claims) throws JsonProcessingException {
        String header = Base64.getEncoder().encodeToString("{\"alg\":\"none\"}".getBytes());
        byte[] jsonClaims = JsonSupport.getObjectMapper().writeValueAsBytes(claims);
        String payload = Base64.getEncoder().encodeToString(jsonClaims);
        return header + "." + payload;
    }

    private String validTokenA() throws JsonProcessingException {
        return bearerTokenWith(Map.of(
                "sub", USER_A_ID,
                "email_verified", "true",
                "name", "Alice Scheduler",
                "email", "alice-sched@example.com"
        ));
    }

    private String validTokenB() throws JsonProcessingException {
        return bearerTokenWith(Map.of(
                "sub", USER_B_ID,
                "email_verified", "true",
                "name", "Bob Scheduler",
                "email", "bob-sched@example.com"
        ));
    }

    // --- US1: Create and Activate ---

    @Test
    public void testCreateDailyScheduleReturns201WithScheduleId() throws JsonProcessingException {
        var request = new PodcastScheduleEndpoint.CreateScheduleRequest(
                "machine learning", "DAILY", 10, 0, null, null);

        var response = httpClient.POST("/schedules")
                .addHeader("Authorization", "Bearer " + validTokenA())
                .withRequestBody(request)
                .responseBodyAs(PodcastScheduleEntity.ScheduleCreatedResponse.class)
                .invoke();

        assertThat(response.status().intValue()).isEqualTo(201);
        assertThat(response.body().scheduleId()).isNotBlank();
        assertThat(response.body().nextRunAt()).isNotNull();
    }

    @Test
    public void testCreateWeeklyScheduleReturns201() throws JsonProcessingException {
        var request = new PodcastScheduleEndpoint.CreateScheduleRequest(
                "AI research", "WEEKLY", 9, 0, "MONDAY", null);

        var response = httpClient.POST("/schedules")
                .addHeader("Authorization", "Bearer " + validTokenA())
                .withRequestBody(request)
                .responseBodyAs(PodcastScheduleEntity.ScheduleCreatedResponse.class)
                .invoke();

        assertThat(response.status().intValue()).isEqualTo(201);
        assertThat(response.body().scheduleId()).isNotBlank();
    }

    @Test
    public void testCreateBlankSearchTermsReturns400() throws JsonProcessingException {
        var request = new PodcastScheduleEndpoint.CreateScheduleRequest(
                "", "DAILY", 10, 0, null, null);

        var response = httpClient.POST("/schedules")
                .addHeader("Authorization", "Bearer " + validTokenA())
                .withRequestBody(request)
                .invoke();

        assertThat(response.status().intValue()).isEqualTo(400);
    }

    @Test
    public void testCreateMonthlyWithDayOfMonth29Returns400() throws JsonProcessingException {
        var request = new PodcastScheduleEndpoint.CreateScheduleRequest(
                "quantum computing", "MONTHLY", 10, 0, null, 29);

        var response = httpClient.POST("/schedules")
                .addHeader("Authorization", "Bearer " + validTokenA())
                .withRequestBody(request)
                .invoke();

        assertThat(response.status().intValue()).isEqualTo(400);
    }

    @Test
    public void testCreateWeeklyWithoutDayOfWeekReturns400() throws JsonProcessingException {
        var request = new PodcastScheduleEndpoint.CreateScheduleRequest(
                "deep learning", "WEEKLY", 10, 0, null, null);

        var response = httpClient.POST("/schedules")
                .addHeader("Authorization", "Bearer " + validTokenA())
                .withRequestBody(request)
                .invoke();

        assertThat(response.status().intValue()).isEqualTo(400);
    }

    @Test
    public void testListSchedulesContainsCreatedSchedule() throws JsonProcessingException {
        // Use a unique userId to avoid the 10-schedule limit accumulated by other tests
        String listUserId = "auth0|list-test-" + UUID.randomUUID();
        String listToken = bearerTokenWith(Map.of("sub", listUserId));

        // Create a schedule
        var createReq = new PodcastScheduleEndpoint.CreateScheduleRequest(
                "list test topic", "DAILY", 8, 30, null, null);
        var createResp = httpClient.POST("/schedules")
                .addHeader("Authorization", "Bearer " + listToken)
                .withRequestBody(createReq)
                .responseBodyAs(PodcastScheduleEntity.ScheduleCreatedResponse.class)
                .invoke();
        assertThat(createResp.status().intValue()).isEqualTo(201);
        String scheduleId = createResp.body().scheduleId();

        // List schedules — view is eventually consistent, wait for the schedule to appear
        Awaitility.await()
                .atMost(60, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    var listResp = httpClient.GET("/schedules")
                            .addHeader("Authorization", "Bearer " + listToken)
                            .responseBodyAs(SchedulesByOwnerView.ScheduleSummaries.class)
                            .invoke();
                    assertThat(listResp.status().intValue()).isEqualTo(200);
                    assertThat(listResp.body().schedules())
                            .anyMatch(s -> s.scheduleId().equals(scheduleId));
                });
    }

    // --- US2: Manage Existing Schedules ---

    @Test
    public void testGetScheduleReturnsSchedule() throws JsonProcessingException {
        var createReq = new PodcastScheduleEndpoint.CreateScheduleRequest(
                "get schedule test", "DAILY", 7, 0, null, null);
        var createResp = httpClient.POST("/schedules")
                .addHeader("Authorization", "Bearer " + validTokenA())
                .withRequestBody(createReq)
                .responseBodyAs(PodcastScheduleEntity.ScheduleCreatedResponse.class)
                .invoke();
        String scheduleId = createResp.body().scheduleId();

        var getResp = httpClient.GET("/schedules/" + scheduleId)
                .addHeader("Authorization", "Bearer " + validTokenA())
                .responseBodyAs(PodcastSchedule.class)
                .invoke();

        assertThat(getResp.status().intValue()).isEqualTo(200);
        assertThat(getResp.body().scheduleId()).isEqualTo(scheduleId);
        assertThat(getResp.body().searchTerms()).isEqualTo("get schedule test");
    }

    @Test
    public void testGetScheduleForOtherUserReturns404() throws JsonProcessingException {
        // User A creates a schedule
        var createReq = new PodcastScheduleEndpoint.CreateScheduleRequest(
                "user A exclusive topic", "DAILY", 7, 0, null, null);
        var createResp = httpClient.POST("/schedules")
                .addHeader("Authorization", "Bearer " + validTokenA())
                .withRequestBody(createReq)
                .responseBodyAs(PodcastScheduleEntity.ScheduleCreatedResponse.class)
                .invoke();
        String scheduleId = createResp.body().scheduleId();

        // User B tries to access it
        var getResp = httpClient.GET("/schedules/" + scheduleId)
                .addHeader("Authorization", "Bearer " + validTokenB())
                .invoke();

        assertThat(getResp.status().intValue()).isEqualTo(404);
    }

    @Test
    public void testPauseScheduleReturns200AndStatusIsPaused() throws JsonProcessingException {
        var createReq = new PodcastScheduleEndpoint.CreateScheduleRequest(
                "pause test", "DAILY", 12, 0, null, null);
        var createResp = httpClient.POST("/schedules")
                .addHeader("Authorization", "Bearer " + validTokenA())
                .withRequestBody(createReq)
                .responseBodyAs(PodcastScheduleEntity.ScheduleCreatedResponse.class)
                .invoke();
        String scheduleId = createResp.body().scheduleId();

        var pauseResp = httpClient.PATCH("/schedules/" + scheduleId + "/pause")
                .addHeader("Authorization", "Bearer " + validTokenA())
                .invoke();
        assertThat(pauseResp.status().intValue()).isEqualTo(200);

        var getResp = httpClient.GET("/schedules/" + scheduleId)
                .addHeader("Authorization", "Bearer " + validTokenA())
                .responseBodyAs(PodcastSchedule.class)
                .invoke();
        assertThat(getResp.body().status()).isEqualTo(PodcastSchedule.Status.PAUSED);
    }

    @Test
    public void testPauseAlreadyPausedReturns400() throws JsonProcessingException {
        var createReq = new PodcastScheduleEndpoint.CreateScheduleRequest(
                "double pause test", "DAILY", 12, 0, null, null);
        var createResp = httpClient.POST("/schedules")
                .addHeader("Authorization", "Bearer " + validTokenA())
                .withRequestBody(createReq)
                .responseBodyAs(PodcastScheduleEntity.ScheduleCreatedResponse.class)
                .invoke();
        String scheduleId = createResp.body().scheduleId();

        httpClient.PATCH("/schedules/" + scheduleId + "/pause")
                .addHeader("Authorization", "Bearer " + validTokenA())
                .invoke();

        var secondPause = httpClient.PATCH("/schedules/" + scheduleId + "/pause")
                .addHeader("Authorization", "Bearer " + validTokenA())
                .invoke();
        assertThat(secondPause.status().intValue()).isEqualTo(400);
    }

    @Test
    public void testResumeScheduleReturns200AndStatusIsActive() throws JsonProcessingException {
        var createReq = new PodcastScheduleEndpoint.CreateScheduleRequest(
                "resume test", "DAILY", 14, 0, null, null);
        var createResp = httpClient.POST("/schedules")
                .addHeader("Authorization", "Bearer " + validTokenA())
                .withRequestBody(createReq)
                .responseBodyAs(PodcastScheduleEntity.ScheduleCreatedResponse.class)
                .invoke();
        String scheduleId = createResp.body().scheduleId();

        // Pause first
        httpClient.PATCH("/schedules/" + scheduleId + "/pause")
                .addHeader("Authorization", "Bearer " + validTokenA())
                .invoke();

        // Resume
        var resumeResp = httpClient.PATCH("/schedules/" + scheduleId + "/resume")
                .addHeader("Authorization", "Bearer " + validTokenA())
                .invoke();
        assertThat(resumeResp.status().intValue()).isEqualTo(200);

        var getResp = httpClient.GET("/schedules/" + scheduleId)
                .addHeader("Authorization", "Bearer " + validTokenA())
                .responseBodyAs(PodcastSchedule.class)
                .invoke();
        assertThat(getResp.body().status()).isEqualTo(PodcastSchedule.Status.ACTIVE);
    }

    @Test
    public void testResumeAlreadyActiveReturns400() throws JsonProcessingException {
        var createReq = new PodcastScheduleEndpoint.CreateScheduleRequest(
                "double resume test", "DAILY", 14, 0, null, null);
        var createResp = httpClient.POST("/schedules")
                .addHeader("Authorization", "Bearer " + validTokenA())
                .withRequestBody(createReq)
                .responseBodyAs(PodcastScheduleEntity.ScheduleCreatedResponse.class)
                .invoke();
        String scheduleId = createResp.body().scheduleId();

        var resumeResp = httpClient.PATCH("/schedules/" + scheduleId + "/resume")
                .addHeader("Authorization", "Bearer " + validTokenA())
                .invoke();
        assertThat(resumeResp.status().intValue()).isEqualTo(400);
    }

    @Test
    public void testUpdateScheduleReturns200AndReflectsNewFields() throws JsonProcessingException {
        var createReq = new PodcastScheduleEndpoint.CreateScheduleRequest(
                "original topic", "DAILY", 9, 0, null, null);
        var createResp = httpClient.POST("/schedules")
                .addHeader("Authorization", "Bearer " + validTokenA())
                .withRequestBody(createReq)
                .responseBodyAs(PodcastScheduleEntity.ScheduleCreatedResponse.class)
                .invoke();
        String scheduleId = createResp.body().scheduleId();

        var updateReq = new PodcastScheduleEndpoint.UpdateScheduleRequest(
                "updated topic", "WEEKLY", 15, 30, "WEDNESDAY", null);
        var updateResp = httpClient.PUT("/schedules/" + scheduleId)
                .addHeader("Authorization", "Bearer " + validTokenA())
                .withRequestBody(updateReq)
                .invoke();
        assertThat(updateResp.status().intValue()).isEqualTo(200);

        var getResp = httpClient.GET("/schedules/" + scheduleId)
                .addHeader("Authorization", "Bearer " + validTokenA())
                .responseBodyAs(PodcastSchedule.class)
                .invoke();
        assertThat(getResp.body().searchTerms()).isEqualTo("updated topic");
        assertThat(getResp.body().cadence()).isEqualTo(PodcastSchedule.Cadence.WEEKLY);
    }

    @Test
    public void testDeleteScheduleReturns200AndSubsequentGetReturns404() throws JsonProcessingException {
        var createReq = new PodcastScheduleEndpoint.CreateScheduleRequest(
                "delete me", "DAILY", 11, 0, null, null);
        var createResp = httpClient.POST("/schedules")
                .addHeader("Authorization", "Bearer " + validTokenA())
                .withRequestBody(createReq)
                .responseBodyAs(PodcastScheduleEntity.ScheduleCreatedResponse.class)
                .invoke();
        String scheduleId = createResp.body().scheduleId();

        var deleteResp = httpClient.DELETE("/schedules/" + scheduleId)
                .addHeader("Authorization", "Bearer " + validTokenA())
                .invoke();
        assertThat(deleteResp.status().intValue()).isEqualTo(200);

        var getResp = httpClient.GET("/schedules/" + scheduleId)
                .addHeader("Authorization", "Bearer " + validTokenA())
                .invoke();
        assertThat(getResp.status().intValue()).isEqualTo(404);
    }

    @Test
    public void testScheduleLimitOf10IsEnforced() throws JsonProcessingException {
        // Use a unique userId so this test starts with 0 schedules regardless of test order
        String limitUserId = "auth0|limit-test-" + UUID.randomUUID();
        String limitToken = bearerTokenWith(Map.of("sub", limitUserId));

        // Create 10 schedules for the unique user
        for (int i = 0; i < 10; i++) {
            var req = new PodcastScheduleEndpoint.CreateScheduleRequest(
                    "limit test topic " + i, "DAILY", 10, i, null, null);
            var resp = httpClient.POST("/schedules")
                    .addHeader("Authorization", "Bearer " + limitToken)
                    .withRequestBody(req)
                    .responseBodyAs(PodcastScheduleEntity.ScheduleCreatedResponse.class)
                    .invoke();
            assertThat(resp.status().intValue()).isEqualTo(201);
        }

        // Wait for view to reflect all 10 schedules before attempting the 11th
        Awaitility.await()
                .atMost(60, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    var listResp = httpClient.GET("/schedules")
                            .addHeader("Authorization", "Bearer " + limitToken)
                            .responseBodyAs(SchedulesByOwnerView.ScheduleSummaries.class)
                            .invoke();
                    assertThat(listResp.body().schedules()).hasSize(10);
                });

        // 11th should fail
        var eleventh = httpClient.POST("/schedules")
                .addHeader("Authorization", "Bearer " + limitToken)
                .withRequestBody(new PodcastScheduleEndpoint.CreateScheduleRequest(
                        "eleventh topic", "DAILY", 10, 0, null, null))
                .invoke();
        assertThat(eleventh.status().intValue()).isEqualTo(400);
    }

    // --- US3: View Execution History ---

    @Test
    public void testGetScheduleRunsOnNewScheduleReturnsEmptyList() throws JsonProcessingException {
        var createReq = new PodcastScheduleEndpoint.CreateScheduleRequest(
                "runs test topic", "DAILY", 6, 0, null, null);
        var createResp = httpClient.POST("/schedules")
                .addHeader("Authorization", "Bearer " + validTokenA())
                .withRequestBody(createReq)
                .responseBodyAs(PodcastScheduleEntity.ScheduleCreatedResponse.class)
                .invoke();
        String scheduleId = createResp.body().scheduleId();

        var runsResp = httpClient.GET("/schedules/" + scheduleId + "/runs")
                .addHeader("Authorization", "Bearer " + validTokenA())
                .responseBodyAs(PodcastScheduleEndpoint.RunsResponse.class)
                .invoke();

        assertThat(runsResp.status().intValue()).isEqualTo(200);
        assertThat(runsResp.body().runs()).isEmpty();
    }

    @Test
    public void testGetScheduleRunsForOtherUserReturns404() throws JsonProcessingException {
        // User A creates a schedule
        var createReq = new PodcastScheduleEndpoint.CreateScheduleRequest(
                "runs isolation test", "DAILY", 6, 0, null, null);
        var createResp = httpClient.POST("/schedules")
                .addHeader("Authorization", "Bearer " + validTokenA())
                .withRequestBody(createReq)
                .responseBodyAs(PodcastScheduleEntity.ScheduleCreatedResponse.class)
                .invoke();
        String scheduleId = createResp.body().scheduleId();

        // User B tries to get runs
        var runsResp = httpClient.GET("/schedules/" + scheduleId + "/runs")
                .addHeader("Authorization", "Bearer " + validTokenB())
                .invoke();

        assertThat(runsResp.status().intValue()).isEqualTo(404);
    }
}
