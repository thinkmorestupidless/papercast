package com.example.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record UserAccount(String userId, String name, String email, List<SavedQuery> savedQueries) {

    public record SavedQuery(String id, String queryText, Instant savedAt) {}

    public static UserAccount create(String userId, String name, String email) {
        return new UserAccount(userId, name, email, List.of());
    }

    public UserAccount withProfileUpdated(String newName, String newEmail) {
        return new UserAccount(userId, newName, newEmail, savedQueries);
    }

    public UserAccount withQuerySaved(String queryText) {
        if (hasQuery(queryText)) {
            return this;
        }
        var query = new SavedQuery(UUID.randomUUID().toString(), queryText, Instant.now());
        var updated = new ArrayList<>(savedQueries);
        updated.add(query);
        return new UserAccount(userId, name, email, List.copyOf(updated));
    }

    public UserAccount withQueryDeleted(String queryId) {
        if (findQuery(queryId).isEmpty()) {
            throw new IllegalArgumentException("Query not found: " + queryId);
        }
        var updated = savedQueries.stream()
                .filter(q -> !q.id().equals(queryId))
                .toList();
        return new UserAccount(userId, name, email, updated);
    }

    public boolean hasQuery(String queryText) {
        return savedQueries.stream().anyMatch(q -> q.queryText().equals(queryText));
    }

    public Optional<SavedQuery> findQuery(String queryId) {
        return savedQueries.stream().filter(q -> q.id().equals(queryId)).findFirst();
    }
}
