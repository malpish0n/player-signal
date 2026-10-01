package com.playersignal.report;
import com.playersignal.game.GameRepository;
import com.playersignal.steam.SteamClient;
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
class ReviewExportIntegrationTest {
 @Container @ServiceConnection static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine");
 @Autowired GameRepository games;@Autowired JdbcTemplate jdbc;@Autowired TestRestTemplate http;
 UUID game;
 @BeforeEach void setup(){game=games.save(new SteamClient.GameDetails(620,"Export fixture",null)).id();jdbc.update("DELETE FROM playersignal.review WHERE game_id=?",game);}
 void seed(int count){jdbc.update("""
 INSERT INTO playersignal.review(id,game_id,steam_recommendation_id,language,review_text,voted_up,votes_up,playtime_minutes,created_at_steam,updated_at_steam,raw_payload)
 SELECT gen_random_uuid(),?,n::text,CASE WHEN n%2=0 THEN 'polish' ELSE 'english' END,'Synthetic 100% feedback '||n,n%2=0,0,12,'2026-10-01T00:00:00Z'::timestamptz+n*interval '1 second','2026-10-01T00:00:00Z'::timestamptz,'{}'::jsonb FROM generate_series(1,?) n
 """,game,count);}
 String path(){return "/api/games/"+game+"/reviews/export";}
 @Test void exportsAllPagesInStableOrderAndOmitsRawPayloads(){seed(105);var response=http.getForEntity(path(),String.class);assertThat(response.getStatusCode().value()).isEqualTo(200);assertThat(response.getHeaders().getFirst("Content-Disposition")).contains("attachment","playersignal-"+game);assertThat(response.getHeaders().getFirst("Cache-Control")).isEqualTo("no-store");assertThat(response.getBody()).startsWith("\uFEFF\"review_id\"");var lines=response.getBody().split("\r\n");assertThat(lines).hasSize(106);assertThat(lines[1]).contains("feedback 105");assertThat(lines[105]).contains("feedback 1\"");assertThat(lines[0]).doesNotContain("raw_payload","author");}
 @Test void appliesSameFiltersAsExplorerAndProducesHeaderForNoMatches(){seed(6);var response=http.getForEntity(path()+"?language=english&votedUp=false&q={search}",String.class,"100%");assertThat(response.getStatusCode().value()).isEqualTo(200);assertThat(response.getBody().split("\r\n")).hasSize(4);assertThat(response.getBody()).doesNotContain("polish");assertThat(http.getForEntity(path()+"?q=absent",String.class).getBody().split("\r\n")).hasSize(1);}
 @Test void rejectsOversizedExportsInsteadOfTruncatingAndValidatesFilters(){seed(5001);var response=http.getForEntity(path(),String.class);assertThat(response.getStatusCode().value()).isEqualTo(422);assertThat(response.getBody()).contains("EXPORT_TOO_LARGE");assertThat(response.getHeaders().getFirst("Content-Disposition")).isNull();assertThat(http.getForEntity(path()+"?language=INVALID",String.class).getStatusCode().value()).isEqualTo(400);assertThat(http.getForEntity("/api/games/"+UUID.randomUUID()+"/reviews/export",String.class).getStatusCode().value()).isEqualTo(404);}
 @Test void dateFiltersIncludeEntireUtcDayAndMatchCsvAcrossPages() throws Exception {
  seed(105);
  jdbc.update("UPDATE playersignal.review SET created_at_steam='2026-09-30T23:59:59.999Z' WHERE steam_recommendation_id='1' AND game_id=?",game);
  jdbc.update("UPDATE playersignal.review SET created_at_steam='2026-10-01T00:00:00Z' WHERE steam_recommendation_id='2' AND game_id=?",game);
  jdbc.update("UPDATE playersignal.review SET created_at_steam='2026-10-01T23:59:59.999Z' WHERE steam_recommendation_id='3' AND game_id=?",game);
  jdbc.update("UPDATE playersignal.review SET created_at_steam='2026-10-02T00:00:00Z' WHERE steam_recommendation_id='4' AND game_id=?",game);
  String query="?from=2026-10-01&to=2026-10-01";
  var csv=http.getForEntity(path()+query,String.class);
  assertThat(csv.getStatusCode().value()).isEqualTo(200);assertThat(csv.getBody().split("\r\n")).hasSize(104);
  assertThat(csv.getBody()).contains("feedback 2\"","feedback 3\"").doesNotContain("feedback 1\"","feedback 4\"");
  var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
  var first=mapper.readTree(http.getForEntity("/api/games/"+game+"/reviews"+query+"&size=100",String.class).getBody());
  var next=mapper.readTree(http.getForEntity("/api/games/"+game+"/reviews"+query+"&size=100&page=1",String.class).getBody());
  assertThat(first.path("total").asInt()).isEqualTo(103);assertThat(first.path("items").size()).isEqualTo(100);assertThat(next.path("items").size()).isEqualTo(3);
  assertThat(http.getForEntity(path()+"?from=2026-10-02&to=2026-10-01",String.class).getStatusCode().value()).isEqualTo(400);
  assertThat(http.getForEntity("/api/games/"+game+"/reviews?from=2026-02-29",String.class).getStatusCode().value()).isEqualTo(400);
 }

}
