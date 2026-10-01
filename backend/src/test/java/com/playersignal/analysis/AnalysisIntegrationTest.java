package com.playersignal.analysis;

import com.playersignal.game.GameRepository;
import com.playersignal.review.ReviewRepository;
import com.playersignal.steam.SteamClient;
import java.time.Duration;
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
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={"playersignal.analysis.provider=openai", "playersignal.analysis.model=gpt-4o-mini-2024-07-18", "spring.datasource.password=test", "playersignal.analysis.max-reviews=3", "playersignal.analysis.retry-delay-ms=0"})
class AnalysisIntegrationTest {
    @Container @ServiceConnection static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");
    @MockitoBean ReviewAnalyzer analyzer;
    @Autowired AnalysisService service;
    @Autowired AnalysisRepository analyses;
    @Autowired ReviewRepository reviews;
    @Autowired GameRepository games;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestRestTemplate http;
    UUID game;
    @BeforeEach void setup() {
        reset(analyzer);
        when(analyzer.available()).thenReturn(true);
        when(analyzer.analyze(any())).thenAnswer(call -> new FixtureReviewAnalyzer().analyze(call.getArgument(0)));
        jdbc.update("DELETE FROM playersignal.review_analysis");
        jdbc.update("DELETE FROM playersignal.analysis_run");
        jdbc.update("DELETE FROM playersignal.review");
        jdbc.update("DELETE FROM playersignal.game");
        game = games.save(new SteamClient.GameDetails(620, "Synthetic test game", null)).id();
    }
    UUID review(String text) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO playersignal.review(id,game_id,steam_recommendation_id,language,review_text,voted_up,votes_up,playtime_minutes,created_at_steam,updated_at_steam,raw_payload)
                VALUES (?, ?, ?, 'english', ?, false, 0, 60, now(), now(), '{}'::jsonb)
                """, id, game, id.toString(), text);
        return id;
    }
    AnalysisRepository.Run finish() {
        await().atMost(Duration.ofSeconds(10)).until(() -> analyses.latest(game) != null && !analyses.latest(game).status().equals("RUNNING"));
        // Wait for the worker's connection-bound lock to be released before starting another run.
        await().atMost(Duration.ofSeconds(5)).until(() -> jdbc.queryForObject("SELECT count(*) FROM pg_locks WHERE locktype='advisory' AND granted", Integer.class) == 0);
        return analyses.latest(game);
    }
    @Test void skipsCachesAndAvoidsReanalysisButPreservesHistoryAfterEdit() {
        UUID first = review("It crashes when joining a lobby.");
        UUID second = review("It crashes when joining a lobby.");
        review("gg");
        service.start(game, false);
        var run = finish();
        assertThat(run.succeeded()).isEqualTo(2); assertThat(run.cached()).isEqualTo(1); assertThat(run.skipped()).isEqualTo(1);
        verify(analyzer, times(1)).analyze(any());
        service.start(game, false); assertThat(finish().processed()).isZero();
        jdbc.update("UPDATE playersignal.review SET votes_up=20 WHERE id=?", second);
        service.start(game, false); assertThat(finish().processed()).isZero();
        jdbc.update("UPDATE playersignal.review SET review_text='The frame rate drops after update.' WHERE id=?", first);
        assertThat(analyses.counts(game).pending()).isEqualTo(1);
        assertThat(analyses.current(java.util.List.of(first))).isEmpty();
        service.start(game, false); assertThat(finish().succeeded()).isEqualTo(1);
        var history = analyses.history(game, first);
        assertThat(history).hasSize(2);
        assertThat(history.stream().map(AnalysisRepository.History::sourceText)).contains("It crashes when joining a lobby.", "The frame rate drops after update.");
        assertThat(reviews.list(game, 0, 20, null, null).items().stream().filter(r -> r.id().equals(first)).findFirst().orElseThrow().analysis().result().path("primaryCategory").asText()).isEqualTo("PERFORMANCE");
    }
    @Test void failureHasBoundedRetriesAndRequiresExplicitRetry() {
        UUID id = review("It crashes on join.");
        doThrow(AnalysisFailure.invalid()).when(analyzer).analyze(any());
        service.start(game, false); assertThat(finish().status()).isEqualTo("COMPLETED_WITH_ERRORS");
        assertThat(analyses.history(game, id).getFirst().analysis().attempts()).isEqualTo(3);
        service.start(game, false); assertThat(finish().processed()).isZero();
        doAnswer(call -> new FixtureReviewAnalyzer().analyze(call.getArgument(0))).when(analyzer).analyze(any());
        service.start(game, true); assertThat(finish().succeeded()).isEqualTo(1);
        assertThat(analyses.history(game, id)).hasSize(1);
        assertThat(analyses.history(game, id).getFirst().analysis().attempts()).isEqualTo(4);
    }
    @Test void transientFailureRetriesAndSourceChangesInFlightRemainPending() throws Exception {
        UUID id = review("It crashes on join.");
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        doThrow(AnalysisFailure.invalid()).doAnswer(call -> {
            entered.countDown(); release.await(5, TimeUnit.SECONDS);
            return new FixtureReviewAnalyzer().analyze(call.getArgument(0));
        }).when(analyzer).analyze(any());
        service.start(game, false);
        try {
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(http.postForEntity("/api/games/"+game+"/analysis", null, String.class).getStatusCode().value()).isEqualTo(409);
            jdbc.update("UPDATE playersignal.review SET review_text='New review text' WHERE id=?", id);
        } finally { release.countDown(); finish(); }
        assertThat(analyses.counts(game).pending()).isEqualTo(1);
        assertThat(analyses.current(java.util.List.of(id))).isEmpty();
        assertThat(analyses.history(game,id).getFirst().analysis().attempts()).isEqualTo(2);
    }
    @Test void providerConfigurationErrorsStopTheBatchAndDemoDoesNotWrite() {
        review("It crashes on join."); review("The frame rate drops after update.");
        doThrow(new AnalysisFailure("AUTH", "Check credentials", false, true)).when(analyzer).analyze(any());
        service.start(game, false);
        var run = finish(); assertThat(run.status()).isEqualTo("FAILED"); assertThat(run.failed()).isEqualTo(1);
        verify(analyzer, times(1)).analyze(any());
        assertThat(http.getForEntity("/api/analysis/demo", String.class).getBody()).contains("SYNTHETIC_DEMO", "fixture-1");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM playersignal.review_analysis", Integer.class)).isEqualTo(1);
    }
    @Test void capAndVersionChangesAreVisibleAndInterruptedRowsRecover() {
        for (int i=0; i<4; i++) review("I love the puzzles " + i);
        service.start(game, false); assertThat(finish().status()).isEqualTo("PARTIAL");
        assertThat(analyses.counts(game).pending()).isEqualTo(1);
        service.start(game, false); assertThat(finish().status()).isEqualTo("COMPLETED");
        jdbc.update("UPDATE playersignal.review_analysis SET prompt_version='old-version'");
        assertThat(analyses.counts(game).pending()).isEqualTo(4);
        var run = analyses.createRun(game);
        var source = analyses.candidates(game, false).getFirst();
        analyses.begin(jdbc, source, run.id());
        service.recover();
        assertThat(analyses.latest(game).status()).isEqualTo("FAILED");
        assertThat(analyses.latest(game).failed()).isEqualTo(1);
        assertThat(analyses.counts(game).failed()).isEqualTo(1);
    }
    @Test void missingConfigurationAndCrossGameHistoryAreRejected() {
        UUID review = review("It crashes on join.");
        when(analyzer.available()).thenReturn(false);
        assertThat(http.postForEntity("/api/games/"+game+"/analysis", null, String.class).getStatusCode().value()).isEqualTo(503);
        assertThat(analyses.latest(game)).isNull();
        assertThat(http.getForEntity("/api/games/"+UUID.randomUUID()+"/reviews/"+review+"/analyses", String.class).getStatusCode().value()).isEqualTo(404);
    }
}
