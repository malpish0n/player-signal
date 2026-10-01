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
 SELECT gen_random_uuid(),?,n::text,CASE WHEN n%2=0 THEN 'polish' ELSE 'english' END,'Synthetic 100% feedback '||n,n%2=0,0,12,'2026-10-01'::timestamptz+n*interval '1 second','2026-10-01'::timestamptz,'{}'::jsonb FROM generate_series(1,?) n
 """,game,count);}
 String path(){return "/api/games/"+game+"/reviews/export";}
 @Test void exportsAllPagesInStableOrderAndOmitsRawPayloads(){seed(105);var response=http.getForEntity(path(),String.class);assertThat(response.getStatusCode().value()).isEqualTo(200);assertThat(response.getHeaders().getFirst("Content-Disposition")).contains("attachment","playersignal-"+game);assertThat(response.getHeaders().getFirst("Cache-Control")).isEqualTo("no-store");assertThat(response.getBody()).startsWith("\uFEFF\"review_id\"");var lines=response.getBody().split("\r\n");assertThat(lines).hasSize(106);assertThat(lines[1]).contains("feedback 105");assertThat(lines[105]).contains("feedback 1\"");assertThat(lines[0]).doesNotContain("raw_payload","author");}
 @Test void appliesSameFiltersAsExplorerAndProducesHeaderForNoMatches(){seed(6);var response=http.getForEntity(path()+"?language=english&votedUp=false&q={search}",String.class,"100%");assertThat(response.getStatusCode().value()).isEqualTo(200);assertThat(response.getBody().split("\r\n")).hasSize(4);assertThat(response.getBody()).doesNotContain("polish");assertThat(http.getForEntity(path()+"?q=absent",String.class).getBody().split("\r\n")).hasSize(1);}
 @Test void rejectsOversizedExportsInsteadOfTruncatingAndValidatesFilters(){seed(5001);var response=http.getForEntity(path(),String.class);assertThat(response.getStatusCode().value()).isEqualTo(422);assertThat(response.getBody()).contains("EXPORT_TOO_LARGE");assertThat(response.getHeaders().getFirst("Content-Disposition")).isNull();assertThat(http.getForEntity(path()+"?language=INVALID",String.class).getStatusCode().value()).isEqualTo(400);assertThat(http.getForEntity("/api/games/"+UUID.randomUUID()+"/reviews/export",String.class).getStatusCode().value()).isEqualTo(404);}
}
