package com.playersignal.overview;
import com.playersignal.game.GameRepository;
import com.playersignal.steam.SteamClient;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;
@Testcontainers @SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class OverviewIntegrationTest {
 @Container @ServiceConnection static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine");
 @Autowired OverviewService service;@Autowired GameRepository games;@Autowired JdbcTemplate jdbc;@Autowired TestRestTemplate http;
 UUID game;final Instant now=Instant.parse("2026-10-01T12:00:00Z");
 @BeforeEach void setup(){game=games.save(new SteamClient.GameDetails(620,"Overview test",null)).id();jdbc.update("DELETE FROM playersignal.review WHERE game_id=?",game);}
 void add(String date,boolean positive){jdbc.update("INSERT INTO playersignal.review(id,game_id,steam_recommendation_id,language,review_text,voted_up,votes_up,playtime_minutes,created_at_steam,updated_at_steam,raw_payload) VALUES (?,?,?,'english','Synthetic',?,0,0,?,?,'{}'::jsonb)",UUID.randomUUID(),game,UUID.randomUUID().toString(),positive,Timestamp.from(Instant.parse(date)),Timestamp.from(now));}
 @Test void comparesEqualWindowsExcludesFutureAndZeroFillsDays(){add("2026-09-24T12:00:00Z",true);add("2026-09-24T11:59:59Z",false);add("2026-09-17T12:00:00Z",true);add("2026-10-01T12:00:00Z",false);
  var d=service.get(game,7,now);assertThat(d.imported()).isEqualTo(4);assertThat(d.metrics().reviews()).isEqualTo(1);assertThat(d.metrics().previousReviews()).isEqualTo(2);assertThat(d.metrics().positiveRatio()).isEqualTo(1);assertThat(d.metrics().reviewChangePercent()).isEqualTo(-50);assertThat(d.metrics().positiveChangePoints()).isEqualTo(50);assertThat(d.daily()).hasSize(8);assertThat(d.daily().stream().mapToLong(x->x.recommended()+x.notRecommended()).sum()).isEqualTo(1);assertThat(d.categories()).isEmpty();assertThat(d.analysis().counts().pending()).isEqualTo(4);
 }
 @Test void emptyMetricsAndInvalidRangesAreExplicit(){var d=service.get(game,30,now);assertThat(d.metrics().positiveRatio()).isNull();assertThat(d.metrics().reviewChangePercent()).isNull();assertThat(d.issues().snapshot().builtAt()).isNull();assertThat(http.getForEntity("/api/games/"+game+"/overview?days=365",String.class).getStatusCode().value()).isEqualTo(400);assertThat(http.getForEntity("/api/games/"+UUID.randomUUID()+"/overview",String.class).getStatusCode().value()).isEqualTo(404);}
 @Test void reviewSearchIsLiteralAndBounded(){add("2026-09-29T12:00:00Z",true);jdbc.update("UPDATE playersignal.review SET review_text='100% playable' WHERE game_id=?",game);assertThat(http.getForEntity("/api/games/"+game+"/reviews?q=Synthetic",String.class).getBody()).contains("\"total\":0");assertThat(http.getForEntity("/api/games/"+game+"/reviews?q=PLAYABLE",String.class).getBody()).contains("\"total\":1");assertThat(http.getForEntity("/api/games/"+game+"/reviews?q="+"a".repeat(257),String.class).getStatusCode().value()).isEqualTo(400);}
 @Test void demoUsesExplicitFixedFixturesAndWritesNothing(){var before=jdbc.queryForObject("SELECT count(*) FROM playersignal.review",Integer.class);var d=OverviewService.demo(30);assertThat(d.mode()).isEqualTo("SYNTHETIC_DEMO");assertThat(d.metrics().reviews()).isEqualTo(10);assertThat(d.metrics().positiveRatio()).isEqualTo(.4);assertThat(d.categories().stream().mapToLong(OverviewService.Category::count).sum()).isEqualTo(10);assertThat(d.issues().total()).isEqualTo(3);assertThat(http.getForEntity("/api/overview/demo",String.class).getBody()).contains("SYNTHETIC_DEMO");assertThat(jdbc.queryForObject("SELECT count(*) FROM playersignal.review",Integer.class)).isEqualTo(before);}
}
