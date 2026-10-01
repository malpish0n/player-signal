package com.playersignal.update;
import com.playersignal.game.GameRepository;
import com.playersignal.steam.SteamClient;
import java.util.*;
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
class UpdateIntegrationTest {
 @Container @ServiceConnection static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine");
 @Autowired GameRepository games;@Autowired JdbcTemplate jdbc;@Autowired TestRestTemplate http;
 UUID game;String path;
 @BeforeEach void setup(){game=games.save(new SteamClient.GameDetails(620,"Updates fixture",null)).id();jdbc.update("DELETE FROM playersignal.game_update WHERE game_id=?",game);path="/api/games/"+game+"/updates";}
 @Test void createEditAndRejectStaleOrWrongGameUpdates(){
  var first=http.postForEntity(path,new UpdateController.Input(" Patch 1 ","2026-09-20",null),UpdateController.Update.class);assertThat(first.getStatusCode().value()).isEqualTo(200);var saved=first.getBody();assertThat(saved.title()).isEqualTo("Patch 1");
  var edit=http.postForEntity(path+"/"+saved.id(),new UpdateController.Input("Patch 2","2026-09-21",0),UpdateController.Update.class);assertThat(edit.getBody().version()).isEqualTo(1);assertThat(edit.getBody().releasedOn().toString()).isEqualTo("2026-09-21");
  assertThat(http.postForEntity(path+"/"+saved.id(),new UpdateController.Input("Stale","2026-09-20",0),String.class).getStatusCode().value()).isEqualTo(409);
  UUID other=games.save(new SteamClient.GameDetails(621,"Other",null)).id();assertThat(http.postForEntity("/api/games/"+other+"/updates/"+saved.id(),new UpdateController.Input("Wrong game","2026-09-20",1),String.class).getStatusCode().value()).isEqualTo(404);
  var list=http.getForObject(path,UpdateController.Update[].class);assertThat(list).hasSize(1);assertThat(list[0].title()).isEqualTo("Patch 2");
 }
 @Test void validatesInputsAndBoundsSavedUpdates(){
  for(var input:List.of(new UpdateController.Input(" ","2026-01-01",null),new UpdateController.Input("a".repeat(121),"2026-01-01",null),new UpdateController.Input("Line\nbreak","2026-01-01",null),new UpdateController.Input("Patch","2026-02-29",null),new UpdateController.Input("Patch","",null)))assertThat(http.postForEntity(path,input,String.class).getStatusCode().value()).isEqualTo(400);
  jdbc.update("INSERT INTO playersignal.game_update(id,game_id,title,released_on) SELECT gen_random_uuid(),?,'Fixture',DATE '2026-01-01' FROM generate_series(1,100)",game);
  assertThat(http.postForEntity(path,new UpdateController.Input("Extra","2026-01-01",null),String.class).getStatusCode().value()).isEqualTo(422);assertThat(http.getForObject(path,UpdateController.Update[].class)).hasSize(100);
 }
}
