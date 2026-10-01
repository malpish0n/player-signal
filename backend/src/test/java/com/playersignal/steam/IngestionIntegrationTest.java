package com.playersignal.steam;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.playersignal.game.GameRepository;
import com.playersignal.review.ReviewRepository;
import com.playersignal.shared.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
        "spring.datasource.password=test", "playersignal.ingestion.max-pages=2", "playersignal.ingestion.page-delay-ms=0"})
class IngestionIntegrationTest {
    @Container @ServiceConnection static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");
    @MockitoBean SteamClient steam;
    @Autowired GameRepository games;
    @Autowired ReviewRepository reviews;
    @Autowired IngestionRepository runs;
    @Autowired IngestionService ingestion;
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    UUID game;
    @BeforeEach void setup() {
        reset(steam);
        jdbc.update("DELETE FROM playersignal.ingestion_run");
        jdbc.update("DELETE FROM playersignal.review");
        jdbc.update("DELETE FROM playersignal.game");
        game = games.save(new SteamClient.GameDetails(620, "Fixture game", null)).id();
    }
    SteamClient.SourceReview review(String id, String text, boolean positive, int revision) throws Exception {
        var raw = mapper.createObjectNode().put("recommendationid", id).put("review", text).put("voted_up", positive).put("revision", revision);
        return new SteamClient.SourceReview(id, "english", text, positive, 0, 60,
                Instant.ofEpochSecond(1700000000), Instant.ofEpochSecond(1700000000 + revision), raw);
    }
    IngestionRepository.Run finish() {
        await().atMost(Duration.ofSeconds(10)).until(() -> runs.latest(game) != null && !runs.latest(game).status().equals("RUNNING"));
        return runs.latest(game);
    }
    @Test void resyncIsIdempotentAndChangedReviewsKeepTheirIdentity() throws Exception {
        var first = review("1", " exact original ", false, 0);
        when(steam.reviews(620, "*")).thenReturn(new SteamClient.ReviewPage(List.of(first), "next"));
        when(steam.reviews(620, "next")).thenReturn(new SteamClient.ReviewPage(List.of(), ""));
        ingestion.start(game);
        assertThat(finish().inserted()).isEqualTo(1);
        UUID originalId = reviews.list(game, 0, 20, null, null).items().getFirst().id();
        ingestion.start(game);
        var repeat = finish();
        assertThat(repeat.inserted()).isZero(); assertThat(repeat.updated()).isZero();
        when(steam.reviews(620, "*")).thenReturn(new SteamClient.ReviewPage(List.of(review("1", " edited text ", true, 1)), "next"));
        ingestion.start(game);
        assertThat(finish().updated()).isEqualTo(1);
        var page = reviews.list(game, 0, 20, "english", true);
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items().getFirst().id()).isEqualTo(originalId);
        assertThat(page.items().getFirst().reviewText()).isEqualTo(" edited text ");
        assertThat(reviews.list(game, 0, 20, null, false).total()).isZero();
    }
    @Test void failedPageKeepsCommittedReviewsAndResumesAtSavedCursor() throws Exception {
        when(steam.reviews(620, "*")).thenReturn(new SteamClient.ReviewPage(List.of(review("1", "kept", false, 0)), "page2"));
        when(steam.reviews(620, "page2")).thenThrow(new ApiException(502, "STEAM_UNAVAILABLE", "Steam unavailable"));
        ingestion.start(game);
        var failed = finish();
        assertThat(failed.status()).isEqualTo("FAILED"); assertThat(failed.fetched()).isEqualTo(1);
        assertThat(failed.nextCursor()).isEqualTo("page2"); assertThat(failed.error()).contains("Steam unavailable");
        doReturn(new SteamClient.ReviewPage(List.of(), "")).when(steam).reviews(620, "page2");
        ingestion.start(game); assertThat(finish().status()).isEqualTo("COMPLETED");
        verify(steam, times(1)).reviews(620, "*");
        assertThat(reviews.list(game, 0, 20, null, null).total()).isEqualTo(1);
    }
    @Test void capIsExplicitAndNextImportContinues() throws Exception {
        when(steam.reviews(eq(620L), anyString())).thenAnswer(call -> {
            String cursor = call.getArgument(1);
            return new SteamClient.ReviewPage(List.of(review(cursor.equals("*") ? "1" : "2", cursor, true, 0)), cursor + "n");
        });
        ingestion.start(game);
        var partial = finish();
        assertThat(partial.status()).isEqualTo("PARTIAL"); assertThat(partial.fetched()).isEqualTo(2);
        when(steam.reviews(620, "*nn")).thenReturn(new SteamClient.ReviewPage(List.of(), ""));
        ingestion.start(game); assertThat(finish().status()).isEqualTo("COMPLETED");
    }
    @Test void concurrentImportReturnsConflict() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        when(steam.reviews(620, "*")).thenAnswer(call -> {
            entered.countDown(); release.await(5, TimeUnit.SECONDS);
            return new SteamClient.ReviewPage(List.of(), "");
        });
        ingestion.start(game);
        try {
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var response = http.postForEntity("/api/games/" + game + "/sync", null, String.class);
            assertThat(response.getStatusCode().value()).isEqualTo(409);
            assertThat(response.getBody()).contains("SYNC_IN_PROGRESS", "requestId");
        } finally { release.countDown(); finish(); }
    }
    @Test void staleRunsRecoverWithoutDiscardingCursor() {
        var run = runs.create(game);
        jdbc.update("UPDATE playersignal.ingestion_run SET next_cursor='resume' WHERE id=?", run.id());
        ingestion.recoverInterruptedRuns();
        assertThat(runs.latest(game).status()).isEqualTo("FAILED");
        assertThat(runs.latest(game).nextCursor()).isEqualTo("resume");
    }
    @Test void apiValidatesInputAndDeduplicatesGame() {
        when(steam.lookup(620)).thenReturn(new SteamClient.GameDetails(620, "Fixture game", null));
        var added = http.postForEntity("/api/games", java.util.Map.of("steamApp", "https://store.steampowered.com/app/620/"), GameRepository.Game.class);
        assertThat(added.getStatusCode().value()).isEqualTo(200); assertThat(added.getBody().id()).isEqualTo(game);
        assertThat(games.list()).hasSize(1);
        assertThat(http.postForEntity("/api/games", java.util.Map.of("steamApp", "https://evil.test/app/620"), String.class).getStatusCode().value()).isEqualTo(400);
        assertThat(http.getForEntity("/api/games/" + game + "/reviews?size=101", String.class).getStatusCode().value()).isEqualTo(400);
        assertThat(http.getForEntity("/api/games/" + UUID.randomUUID() + "/reviews", String.class).getStatusCode().value()).isEqualTo(404);
        assertThat(http.getForEntity("/api/games/" + game + "/sync/latest", String.class).getStatusCode().value()).isEqualTo(204);
    }
}
