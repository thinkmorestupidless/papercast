package com.example.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.JWT;
import akka.javasdk.annotations.http.Delete;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpException;
import akka.javasdk.http.HttpResponses;
import com.example.application.UserAccountEntity;

import java.util.UUID;

@HttpEndpoint("/account")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@JWT(validate = JWT.JwtMethodMode.BEARER_TOKEN)
public class UserAccountEndpoint extends AbstractHttpEndpoint {

    // --- Request / Response records ---

    public record SaveQueryRequest(String queryText) {}

    public record CreatePodcastResponse(String workflowId, String statusUrl) {}

    // --- Fields ---

    private final ComponentClient componentClient;

    public UserAccountEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    // --- Entity key encoding ---
    // Auth0 user IDs contain '|' which is reserved in Akka's cluster sharding paths.
    // Encode it to avoid routing failures; decode back in the entity.

    private static String entityKey(String userId) {
        return userId.replace("|", "%7C");
    }

    // --- JWT claim extraction ---

    private record Claims(String userId, String name, String email) {}

    private Claims extractClaims() {
        var jwtClaims = requestContext().getJwtClaims();
        String userId = jwtClaims.subject()
                .orElseThrow(() -> HttpException.badRequest("Missing sub claim"));
        // Akka JwtClaims.getString/getBoolean only reads string-typed claims via getStringClaim();
        // pass email_verified as a string "true"/"false" in tokens for compatibility
        boolean emailVerified = jwtClaims.getString("email_verified")
                .map("true"::equalsIgnoreCase)
                .orElse(false);
        if (!emailVerified) {
            throw HttpException.forbidden("Email not verified");
        }
        String name = jwtClaims.getString("name")
                .orElseThrow(() -> HttpException.badRequest("Missing name claim"));
        String email = jwtClaims.getString("email")
                .orElseThrow(() -> HttpException.badRequest("Missing email claim"));
        return new Claims(userId, name, email);
    }

    // --- GET /account/profile ---

    @Get("/profile")
    public UserAccountEntity.ProfileResponse getProfile() {
        var claims = extractClaims();
        return componentClient
                .forKeyValueEntity(entityKey(claims.userId()))
                .method(UserAccountEntity::upsertProfile)
                .invoke(new UserAccountEntity.UpsertProfile(claims.name(), claims.email()));
    }

    // --- POST /account/queries ---

    @Post("/queries")
    public UserAccountEntity.SavedQueryResponse saveQuery(SaveQueryRequest request) {
        var claims = extractClaims();
        return componentClient
                .forKeyValueEntity(entityKey(claims.userId()))
                .method(UserAccountEntity::saveQuery)
                .invoke(new UserAccountEntity.SaveQuery(request.queryText(), claims.name(), claims.email()));
    }

    // --- GET /account/queries ---

    @Get("/queries")
    public UserAccountEntity.SavedQueriesResponse getQueries() {
        var claims = extractClaims();
        return componentClient
                .forKeyValueEntity(entityKey(claims.userId()))
                .method(UserAccountEntity::getQueries)
                .invoke();
    }

    // --- DELETE /account/queries/{queryId} ---

    @Delete("/queries/{queryId}")
    public akka.http.javadsl.model.HttpResponse deleteQuery(String queryId) {
        var claims = extractClaims();
        try {
            componentClient
                    .forKeyValueEntity(entityKey(claims.userId()))
                    .method(UserAccountEntity::deleteQuery)
                    .invoke(new UserAccountEntity.DeleteQuery(queryId, claims.name(), claims.email()));
            return HttpResponses.ok();
        } catch (RuntimeException e) {
            throw HttpException.notFound();
        }
    }

    // --- POST /account/queries/{queryId}/run ---

    @Post("/queries/{queryId}/run")
    public akka.http.javadsl.model.HttpResponse runQuery(String queryId) {
        var claims = extractClaims();
        var queriesResponse = componentClient
                .forKeyValueEntity(entityKey(claims.userId()))
                .method(UserAccountEntity::getQueries)
                .invoke();
        var savedQuery = queriesResponse.queries().stream()
                .filter(q -> q.id().equals(queryId))
                .findFirst()
                .orElseThrow(HttpException::notFound);
        String workflowId = UUID.randomUUID().toString();
        componentClient
                .forWorkflow(workflowId)
                .method(com.example.application.PodcastCreationWorkflow::create)
                .invoke(new com.example.application.PodcastCreationWorkflow.CreateCommand(savedQuery.queryText()));
        return HttpResponses.created(new CreatePodcastResponse(
                workflowId, "/podcast/" + workflowId + "/status"));
    }
}
