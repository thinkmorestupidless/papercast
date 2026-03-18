package com.example.api;

import akka.javasdk.JsonSupport;
import akka.javasdk.testkit.TestKitSupport;
import com.example.application.UserAccountEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class UserAccountEndpointIntegrationTest extends TestKitSupport {

    private static final String USER_A_ID = "auth0|user-a-" + UUID.randomUUID();
    private static final String USER_B_ID = "auth0|user-b-" + UUID.randomUUID();

    private String tokenFor(String userId, String name, String email, boolean emailVerified)
            throws JsonProcessingException {
        return bearerTokenWith(Map.of(
                "sub", userId,
                "email_verified", String.valueOf(emailVerified),
                "name", name,
                "email", email
        ));
    }

    private String validTokenA() throws JsonProcessingException {
        return tokenFor(USER_A_ID, "Alice Smith", "alice@example.com", true);
    }

    private String validTokenB() throws JsonProcessingException {
        return tokenFor(USER_B_ID, "Bob Jones", "bob@example.com", true);
    }

    private String bearerTokenWith(Map<String, Object> claims) throws JsonProcessingException {
        String header = Base64.getEncoder()
                .encodeToString("{\"alg\":\"none\"}".getBytes());
        byte[] jsonClaims = JsonSupport.getObjectMapper().writeValueAsBytes(claims);
        String payload = Base64.getEncoder().encodeToString(jsonClaims);
        return header + "." + payload;
    }

    // --- Profile endpoint tests ---

    @Test
    public void testGetProfileWithNoTokenIsRejected() {
        var response = httpClient.GET("/account/profile").invoke();
        assertThat(response.status().isFailure()).isTrue();
        assertThat(response.status().intValue()).isIn(400, 401);
    }

    @Test
    public void testGetProfileWithUnverifiedEmailReturnsForbidden() throws JsonProcessingException {
        var token = tokenFor(USER_A_ID, "Alice Smith", "alice@example.com", false);
        var response = httpClient.GET("/account/profile")
                .addHeader("Authorization", "Bearer " + token)
                .invoke();
        assertThat(response.status().intValue()).isEqualTo(403);
    }

    @Test
    public void testGetProfileWithMissingNameClaimReturnsBadRequest() throws JsonProcessingException {
        var token = bearerTokenWith(Map.of(
                "sub", USER_A_ID,
                "email_verified", "true",
                "email", "alice@example.com"
                // "name" intentionally omitted
        ));
        var response = httpClient.GET("/account/profile")
                .addHeader("Authorization", "Bearer " + token)
                .invoke();
        assertThat(response.status().intValue()).isEqualTo(400);
    }

    @Test
    public void testGetProfileWithMissingEmailClaimReturnsBadRequest() throws JsonProcessingException {
        var token = bearerTokenWith(Map.of(
                "sub", USER_A_ID,
                "email_verified", "true",
                "name", "Alice Smith"
                // "email" intentionally omitted
        ));
        var response = httpClient.GET("/account/profile")
                .addHeader("Authorization", "Bearer " + token)
                .invoke();
        assertThat(response.status().intValue()).isEqualTo(400);
    }

    @Test
    public void testGetProfileReturnsCorrectUserData() throws JsonProcessingException {
        var response = httpClient.GET("/account/profile")
                .addHeader("Authorization", "Bearer " + validTokenA())
                .responseBodyAs(UserAccountEntity.ProfileResponse.class)
                .invoke();

        assertThat(response.status().intValue()).isEqualTo(200);
        assertThat(response.body().userId()).isEqualTo(USER_A_ID);
        assertThat(response.body().name()).isEqualTo("Alice Smith");
        assertThat(response.body().email()).isEqualTo("alice@example.com");
    }

    // --- Saved Query tests ---

    @Test
    public void testSaveQueryReturns200WithQueryData() throws JsonProcessingException {
        var response = httpClient.POST("/account/queries")
                .addHeader("Authorization", "Bearer " + validTokenA())
                .withRequestBody(new UserAccountEndpoint.SaveQueryRequest("machine learning papers"))
                .responseBodyAs(UserAccountEntity.SavedQueryResponse.class)
                .invoke();

        assertThat(response.status().intValue()).isEqualTo(200);
        assertThat(response.body().id()).isNotBlank();
        assertThat(response.body().queryText()).isEqualTo("machine learning papers");
        assertThat(response.body().savedAt()).isNotBlank();
    }

    @Test
    public void testGetQueriesContainsSavedQuery() throws JsonProcessingException {
        var saveResponse = httpClient.POST("/account/queries")
                .addHeader("Authorization", "Bearer " + validTokenA())
                .withRequestBody(new UserAccountEndpoint.SaveQueryRequest("quantum computing research"))
                .responseBodyAs(UserAccountEntity.SavedQueryResponse.class)
                .invoke();
        assertThat(saveResponse.status().intValue()).isEqualTo(200);
        String savedId = saveResponse.body().id();

        var getResponse = httpClient.GET("/account/queries")
                .addHeader("Authorization", "Bearer " + validTokenA())
                .responseBodyAs(UserAccountEntity.SavedQueriesResponse.class)
                .invoke();

        assertThat(getResponse.status().intValue()).isEqualTo(200);
        assertThat(getResponse.body().queries()).anyMatch(q -> q.id().equals(savedId));
    }

    @Test
    public void testSaveDuplicateQueryIsIdempotent() throws JsonProcessingException {
        String queryText = "deep learning architectures";

        var first = httpClient.POST("/account/queries")
                .addHeader("Authorization", "Bearer " + validTokenA())
                .withRequestBody(new UserAccountEndpoint.SaveQueryRequest(queryText))
                .responseBodyAs(UserAccountEntity.SavedQueryResponse.class)
                .invoke();
        assertThat(first.status().intValue()).isEqualTo(200);

        var second = httpClient.POST("/account/queries")
                .addHeader("Authorization", "Bearer " + validTokenA())
                .withRequestBody(new UserAccountEndpoint.SaveQueryRequest(queryText))
                .responseBodyAs(UserAccountEntity.SavedQueryResponse.class)
                .invoke();
        assertThat(second.status().intValue()).isEqualTo(200);
        assertThat(second.body().id()).isEqualTo(first.body().id());

        var queries = httpClient.GET("/account/queries")
                .addHeader("Authorization", "Bearer " + validTokenA())
                .responseBodyAs(UserAccountEntity.SavedQueriesResponse.class)
                .invoke();
        assertThat(queries.body().queries().stream().filter(q -> q.queryText().equals(queryText)).count()).isEqualTo(1);
    }

    @Test
    public void testSaveBlankQueryReturnsBadRequest() throws JsonProcessingException {
        var response = httpClient.POST("/account/queries")
                .addHeader("Authorization", "Bearer " + validTokenA())
                .withRequestBody(new UserAccountEndpoint.SaveQueryRequest(""))
                .invoke();

        assertThat(response.status().intValue()).isEqualTo(400);
    }

    @Test
    public void testDeleteQueryReturnsOkAndQueryIsGone() throws JsonProcessingException {
        var saveResponse = httpClient.POST("/account/queries")
                .addHeader("Authorization", "Bearer " + validTokenA())
                .withRequestBody(new UserAccountEndpoint.SaveQueryRequest("neural networks to delete"))
                .responseBodyAs(UserAccountEntity.SavedQueryResponse.class)
                .invoke();
        assertThat(saveResponse.status().intValue()).isEqualTo(200);
        String queryId = saveResponse.body().id();

        var deleteResponse = httpClient.DELETE("/account/queries/" + queryId)
                .addHeader("Authorization", "Bearer " + validTokenA())
                .invoke();
        assertThat(deleteResponse.status().intValue()).isEqualTo(200);

        var queries = httpClient.GET("/account/queries")
                .addHeader("Authorization", "Bearer " + validTokenA())
                .responseBodyAs(UserAccountEntity.SavedQueriesResponse.class)
                .invoke();
        assertThat(queries.body().queries().stream().noneMatch(q -> q.id().equals(queryId))).isTrue();
    }

    @Test
    public void testCrossUserDeleteQueryReturnsNotFound() throws JsonProcessingException {
        // User A saves a query
        var saveResponse = httpClient.POST("/account/queries")
                .addHeader("Authorization", "Bearer " + validTokenA())
                .withRequestBody(new UserAccountEndpoint.SaveQueryRequest("cross-user isolation test"))
                .responseBodyAs(UserAccountEntity.SavedQueryResponse.class)
                .invoke();
        assertThat(saveResponse.status().intValue()).isEqualTo(200);
        String queryId = saveResponse.body().id();

        // User B tries to delete user A's query ID — user B has no such query
        var deleteResponse = httpClient.DELETE("/account/queries/" + queryId)
                .addHeader("Authorization", "Bearer " + validTokenB())
                .invoke();
        assertThat(deleteResponse.status().intValue()).isEqualTo(404);
    }

    // --- Run Query tests ---

    @Test
    public void testRunQueryReturns201WithWorkflowId() throws JsonProcessingException {
        var saveResponse = httpClient.POST("/account/queries")
                .addHeader("Authorization", "Bearer " + validTokenA())
                .withRequestBody(new UserAccountEndpoint.SaveQueryRequest("large language models"))
                .responseBodyAs(UserAccountEntity.SavedQueryResponse.class)
                .invoke();
        assertThat(saveResponse.status().intValue()).isEqualTo(200);
        String queryId = saveResponse.body().id();

        var runResponse = httpClient.POST("/account/queries/" + queryId + "/run")
                .addHeader("Authorization", "Bearer " + validTokenA())
                .responseBodyAs(UserAccountEndpoint.CreatePodcastResponse.class)
                .invoke();

        assertThat(runResponse.status().intValue()).isEqualTo(201);
        assertThat(runResponse.body().workflowId()).isNotBlank();
        assertThat(runResponse.body().statusUrl()).startsWith("/podcast/");
    }

    @Test
    public void testRunNonExistentQueryReturnsNotFound() throws JsonProcessingException {
        var runResponse = httpClient.POST("/account/queries/nonexistent-query-id/run")
                .addHeader("Authorization", "Bearer " + validTokenA())
                .invoke();

        assertThat(runResponse.status().intValue()).isEqualTo(404);
    }

    @Test
    public void testRunQueryCrossUserReturnsNotFound() throws JsonProcessingException {
        // User A saves a query
        var saveResponse = httpClient.POST("/account/queries")
                .addHeader("Authorization", "Bearer " + validTokenA())
                .withRequestBody(new UserAccountEndpoint.SaveQueryRequest("cross-user run isolation"))
                .responseBodyAs(UserAccountEntity.SavedQueryResponse.class)
                .invoke();
        assertThat(saveResponse.status().intValue()).isEqualTo(200);
        String queryId = saveResponse.body().id();

        // User B tries to run user A's query ID — user B has no such query
        var runResponse = httpClient.POST("/account/queries/" + queryId + "/run")
                .addHeader("Authorization", "Bearer " + validTokenB())
                .invoke();

        assertThat(runResponse.status().intValue()).isEqualTo(404);
    }

    @Test
    public void testGetProfileReflectsUpdatedNameAfterSubsequentCall() throws JsonProcessingException {
        // First call with original name
        httpClient.GET("/account/profile")
                .addHeader("Authorization", "Bearer " + validTokenA())
                .invoke();

        // Second call with updated name — since GET /profile calls upsertProfile, it updates immediately
        String updatedToken = tokenFor(USER_A_ID, "Alice Updated", "alice@example.com", true);
        var response = httpClient.GET("/account/profile")
                .addHeader("Authorization", "Bearer " + updatedToken)
                .responseBodyAs(UserAccountEntity.ProfileResponse.class)
                .invoke();

        assertThat(response.status().intValue()).isEqualTo(200);
        assertThat(response.body().name()).isEqualTo("Alice Updated");
    }
}
