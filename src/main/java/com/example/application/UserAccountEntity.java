package com.example.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import com.example.domain.UserAccount;

import java.util.List;

@Component(id = "user-account")
public class UserAccountEntity extends KeyValueEntity<UserAccount> {

    // --- Command records ---

    public record UpsertProfile(String name, String email) {}

    public record SaveQuery(String queryText, String name, String email) {}

    public record DeleteQuery(String queryId, String name, String email) {}

    // --- Response records ---

    public record ProfileResponse(String userId, String name, String email) {}

    public record SavedQueryResponse(String id, String queryText, String savedAt) {}

    public record SavedQueriesResponse(List<SavedQueryResponse> queries) {}

    // --- Command handlers ---

    public Effect<ProfileResponse> upsertProfile(UpsertProfile command) {
        String userId = commandContext().entityId().replace("%7C", "|");
        UserAccount current = currentState();
        UserAccount updated = (current == null)
                ? UserAccount.create(userId, command.name(), command.email())
                : current.withProfileUpdated(command.name(), command.email());
        return effects()
                .updateState(updated)
                .thenReply(new ProfileResponse(updated.userId(), updated.name(), updated.email()));
    }

    public Effect<SavedQueryResponse> saveQuery(SaveQuery command) {
        if (command.queryText() == null || command.queryText().isBlank()) {
            return effects().error("Query text must not be blank");
        }
        String userId = commandContext().entityId().replace("%7C", "|");
        UserAccount current = currentState();
        UserAccount withProfile = (current == null)
                ? UserAccount.create(userId, command.name(), command.email())
                : current.withProfileUpdated(command.name(), command.email());

        if (withProfile.hasQuery(command.queryText())) {
            // Idempotent: return existing query, but still persist the profile update
            var existing = withProfile.savedQueries().stream()
                    .filter(q -> q.queryText().equals(command.queryText()))
                    .findFirst()
                    .orElseThrow();
            return effects()
                    .updateState(withProfile)
                    .thenReply(new SavedQueryResponse(
                            existing.id(), existing.queryText(), existing.savedAt().toString()));
        }

        UserAccount updated = withProfile.withQuerySaved(command.queryText());
        var newQuery = updated.savedQueries().get(updated.savedQueries().size() - 1);
        return effects()
                .updateState(updated)
                .thenReply(new SavedQueryResponse(
                        newQuery.id(), newQuery.queryText(), newQuery.savedAt().toString()));
    }

    public Effect<Done> deleteQuery(DeleteQuery command) {
        UserAccount current = currentState();
        if (current == null) {
            return effects().error("Query not found");
        }
        UserAccount withProfile = current.withProfileUpdated(command.name(), command.email());
        try {
            UserAccount updated = withProfile.withQueryDeleted(command.queryId());
            return effects().updateState(updated).thenReply(Done.getInstance());
        } catch (IllegalArgumentException e) {
            return effects().error("Query not found");
        }
    }

    public Effect<SavedQueriesResponse> getQueries() {
        UserAccount current = currentState();
        if (current == null) {
            return effects().reply(new SavedQueriesResponse(List.of()));
        }
        var queries = current.savedQueries().stream()
                .map(q -> new SavedQueryResponse(q.id(), q.queryText(), q.savedAt().toString()))
                .toList();
        return effects().reply(new SavedQueriesResponse(queries));
    }

    public Effect<ProfileResponse> getProfile() {
        UserAccount current = currentState();
        if (current == null) {
            return effects().error("Account not found");
        }
        return effects().reply(new ProfileResponse(current.userId(), current.name(), current.email()));
    }
}
