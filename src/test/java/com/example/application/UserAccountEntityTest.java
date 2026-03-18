package com.example.application;

import akka.javasdk.testkit.KeyValueEntityTestKit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UserAccountEntityTest {

    private static final String USER_ID = "auth0|user123";
    private static final String NAME = "Alice Smith";
    private static final String EMAIL = "alice@example.com";

    @Test
    public void testUpsertProfileOnNewEntityCreatesAccount() {
        var testKit = KeyValueEntityTestKit.of(USER_ID, UserAccountEntity::new);
        var result = testKit.method(UserAccountEntity::upsertProfile)
                .invoke(new UserAccountEntity.UpsertProfile(NAME, EMAIL));

        assertThat(result.isReply()).isTrue();
        var profile = result.getReply();
        assertThat(profile.userId()).isEqualTo(USER_ID);
        assertThat(profile.name()).isEqualTo(NAME);
        assertThat(profile.email()).isEqualTo(EMAIL);
        assertThat(testKit.getState().savedQueries()).isEmpty();
    }

    @Test
    public void testSaveQueryStoresQueryInState() {
        var testKit = KeyValueEntityTestKit.of(USER_ID, UserAccountEntity::new);
        var result = testKit.method(UserAccountEntity::saveQuery)
                .invoke(new UserAccountEntity.SaveQuery("large language models", NAME, EMAIL));

        assertThat(result.isReply()).isTrue();
        var response = result.getReply();
        assertThat(response.queryText()).isEqualTo("large language models");
        assertThat(response.id()).isNotBlank();
        assertThat(response.savedAt()).isNotBlank();
        assertThat(testKit.getState().savedQueries()).hasSize(1);
    }

    @Test
    public void testSaveQueryWithDuplicateQueryTextIsIdempotent() {
        var testKit = KeyValueEntityTestKit.of(USER_ID, UserAccountEntity::new);
        var first = testKit.method(UserAccountEntity::saveQuery)
                .invoke(new UserAccountEntity.SaveQuery("black holes", NAME, EMAIL));
        var firstId = first.getReply().id();

        var second = testKit.method(UserAccountEntity::saveQuery)
                .invoke(new UserAccountEntity.SaveQuery("black holes", NAME, EMAIL));
        var secondId = second.getReply().id();

        assertThat(firstId).isEqualTo(secondId);
        assertThat(testKit.getState().savedQueries()).hasSize(1);
    }

    @Test
    public void testSaveQueryWithBlankQueryTextReturnsError() {
        var testKit = KeyValueEntityTestKit.of(USER_ID, UserAccountEntity::new);
        var result = testKit.method(UserAccountEntity::saveQuery)
                .invoke(new UserAccountEntity.SaveQuery("  ", NAME, EMAIL));

        assertThat(result.isError()).isTrue();
        assertThat(result.getError()).contains("blank");
    }

    @Test
    public void testDeleteQueryRemovesQueryFromState() {
        var testKit = KeyValueEntityTestKit.of(USER_ID, UserAccountEntity::new);
        var saveResult = testKit.method(UserAccountEntity::saveQuery)
                .invoke(new UserAccountEntity.SaveQuery("quantum computing", NAME, EMAIL));
        var queryId = saveResult.getReply().id();

        var deleteResult = testKit.method(UserAccountEntity::deleteQuery)
                .invoke(new UserAccountEntity.DeleteQuery(queryId, NAME, EMAIL));

        assertThat(deleteResult.isReply()).isTrue();
        assertThat(testKit.getState().savedQueries()).isEmpty();
    }

    @Test
    public void testDeleteQueryWithUnknownIdReturnsError() {
        var testKit = KeyValueEntityTestKit.of(USER_ID, UserAccountEntity::new);
        var result = testKit.method(UserAccountEntity::deleteQuery)
                .invoke(new UserAccountEntity.DeleteQuery("nonexistent-id", NAME, EMAIL));

        assertThat(result.isError()).isTrue();
        assertThat(result.getError()).contains("not found");
    }

    @Test
    public void testGetQueriesOnNewEntityReturnsEmptyList() {
        var testKit = KeyValueEntityTestKit.of(USER_ID, UserAccountEntity::new);
        var result = testKit.method(UserAccountEntity::getQueries).invoke();

        assertThat(result.isReply()).isTrue();
        assertThat(result.getReply().queries()).isEmpty();
    }

    @Test
    public void testGetProfileOnNewEntityReturnsError() {
        var testKit = KeyValueEntityTestKit.of(USER_ID, UserAccountEntity::new);
        var result = testKit.method(UserAccountEntity::getProfile).invoke();

        assertThat(result.isError()).isTrue();
        assertThat(result.getError()).contains("not found");
    }
}
