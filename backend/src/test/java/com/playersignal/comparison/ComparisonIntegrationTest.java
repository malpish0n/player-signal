package com.playersignal.comparison;
import com.playersignal.game.GameRepository;
import com.playersignal.steam.SteamClient;
import com.playersignal.shared.ApiException;
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
class ComparisonIntegrationTest {
 @Container @ServiceConnection static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine");
 @Autowired ComparisonService service;@Autowired GameRepository games;@Autowired JdbcTemplate jdbc;@Autowired TestRestTemplate http;
 UUID game;Instant now=Instant.parse("2026-10-01T12:00:00Z");
 @BeforeEach void setup(){game=games.save(new SteamClient.GameDetails(620,"Comparison fixture",null)).id();jdbc.update("DELETE FROM playersignal.review WHERE game_id=?",game);}
 void add(String date,boolean vote){jdbc.update("INSERT INTO playersignal.review(id,game_id,steam_recommendation_id,language,review_text,voted_up,votes_up,playtime_minutes,created_at_steam,updated_at_steam,raw_payload) VALUES (?,?,?,'english','Synthetic',?,0,0,?::timestamptz,?::timestamptz,'{}'::jsonb)",UUID.randomUUID(),game,UUID.randomUUID().toString(),vote,date,date);}
 @Test void comparesEqualUtcWindowsWithExclusiveUpperBoundary(){
  add("2026-09-12T23:59:59.999Z",false);add("2026-09-13T00:00:00Z",true);add("2026-09-19T23:59:59.999Z",false);add("2026-09-20T00:00:00Z",true);add("2026-09-26T23:59:59.999Z",true);add("2026-09-27T00:00:00Z",false);
  var d=service.get(game,"2026-09-20",7,now);assertThat(d.before().reviews()).isEqualTo(2);assertThat(d.after().reviews()).isEqualTo(2);assertThat(d.before().recommendationRate()).isEqualTo(.5);assertThat(d.after().recommendationRate()).isEqualTo(1);assertThat(d.recommendationChangePoints()).isEqualTo(50);assertThat(d.reviewChangePercent()).isEqualTo(0);assertThat(d.imported()).isEqualTo(6);assertThat(d.before().from().toString()).isEqualTo("2026-09-13");assertThat(d.after().to().toString()).isEqualTo("2026-09-26");
 }
 @Test void emptyAndOneSidedSamplesKeepUnknownBaselinesNull(){var empty=service.get(game,"2026-09-20",7,now);assertThat(empty.before().recommendationRate()).isNull();assertThat(empty.recommendationChangePoints()).isNull();assertThat(empty.reviewChangePercent()).isNull();add("2026-09-21T00:00:00Z",false);var d=service.get(game,"2026-09-20",7,now);assertThat(d.after().reviews()).isEqualTo(1);assertThat(d.reviewChangePercent()).isNull();assertThat(d.recommendationChangePoints()).isNull();}
 @Test void rejectsIncompleteInvalidAndOutOfRangePeriods(){for(String date:new String[]{"2026-09-30","2026-02-29","","0001-01-01"})assertThatThrownBy(()->service.get(game,date,7,now)).isInstanceOf(ApiException.class);assertThatThrownBy(()->service.get(game,"2026-09-20",8,now)).isInstanceOf(ApiException.class);assertThat(http.getForEntity("/api/games/"+UUID.randomUUID()+"/comparison?date=2026-01-01",String.class).getStatusCode().value()).isEqualTo(404);}
}
