package com.playersignal.auth;
import com.fasterxml.jackson.databind.*;
import com.playersignal.steam.SteamClient;
import java.net.URI;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@Testcontainers @AutoConfigureMockMvc @SpringBootTest(properties="playersignal.auth.enabled=true")
class AuthIntegrationTest {
 @Container @ServiceConnection static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine");
 @Autowired MockMvc mvc;@Autowired ObjectMapper mapper;@Autowired JdbcTemplate jdbc;@MockitoBean SteamClient steam;
 final String password="synthetic-test-passphrase";
 record Client(MockHttpSession session,String csrf,JsonNode user){}
 Client anonymous()throws Exception{var r=mvc.perform(get("/api/auth/session")).andExpect(status().isOk()).andReturn();return client(r);}
 Client client(MvcResult r)throws Exception{var d=mapper.readTree(r.getResponse().getContentAsString());return new Client((MockHttpSession)r.getRequest().getSession(false),d.path("csrfToken").asText(),d.path("user"));}
 Client register()throws Exception{return register(anonymous(),UUID.randomUUID()+"@example.test");}
 Client register(Client c,String email)throws Exception{return client(mvc.perform(post("/api/auth/register").session(c.session()).header("X-CSRF-TOKEN",c.csrf()).contentType("application/json").content(mapper.writeValueAsString(Map.of("email",email,"password",password,"workspaceName","Test workspace")))).andExpect(status().isOk()).andReturn());}
 UUID game(Client c)throws Exception{when(steam.lookup(620)).thenReturn(new SteamClient.GameDetails(620,"Portal fixture",null));var r=mvc.perform(post("/api/games").session(c.session()).header("X-CSRF-TOKEN",c.csrf()).contentType("application/json").content("{\"steamApp\":\"620\"}")).andExpect(status().isOk()).andReturn();return UUID.fromString(mapper.readTree(r.getResponse().getContentAsString()).path("id").asText());}
 @Test void registrationUsesPasswordHashAndRotatesSessionThenLogoutRevokesIt()throws Exception{
  var anon=anonymous();String old=anon.session().getId();String email=UUID.randomUUID()+"@example.test";var user=register(anon,email);assertThat(user.session().getId()).isNotEqualTo(old);assertThat(anon.session().isInvalid()).isTrue();
  String hash=jdbc.queryForObject("SELECT password_hash FROM playersignal.app_user WHERE email=?",String.class,email);assertThat(hash).startsWith("$2a$12$").doesNotContain(password);
  mvc.perform(get("/api/games").session(user.session())).andExpect(status().isOk());
  mvc.perform(post("/api/auth/logout").session(user.session()).header("X-CSRF-TOKEN",user.csrf())).andExpect(status().isOk());assertThat(user.session().isInvalid()).isTrue();mvc.perform(get("/api/games")).andExpect(status().isUnauthorized());
  var next=anonymous();var signed=client(mvc.perform(post("/api/auth/login").session(next.session()).header("X-CSRF-TOKEN",next.csrf()).contentType("application/json").content(mapper.writeValueAsString(Map.of("email",email,"password",password)))).andExpect(status().isOk()).andReturn());assertThat(signed.user().path("email").asText()).isEqualTo(email);
 }
 @Test void csrfAndAuthenticationProtectReadsAndWrites()throws Exception{
  mvc.perform(get("/api/games")).andExpect(status().isUnauthorized());mvc.perform(post("/api/auth/login").contentType("application/json").content("{}" )).andExpect(status().isForbidden());var c=register();mvc.perform(post("/api/games").session(c.session()).contentType("application/json").content("{\"steamApp\":\"620\"}")).andExpect(status().isForbidden());mvc.perform(get("/api/overview/demo")).andExpect(status().isOk());
 }
 @Test void separateAccountsCanConnectSameAppButCannotAccessEachOthersNestedRoutes()throws Exception{
  var a=register();var b=register();UUID ga=game(a),gb=game(b);assertThat(ga).isNotEqualTo(gb);
  mvc.perform(get("/api/games").session(b.session())).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(ga.toString()))));
  for(String tail:List.of("","/updates","/comparison?date=2026-01-01","/reviews","/reviews/export","/overview","/analysis","/sync/latest","/issues","/issues/"+UUID.randomUUID(),"/reviews/"+UUID.randomUUID()+"/analyses"))mvc.perform(get("/api/games/"+ga+tail).session(b.session())).andExpect(status().isNotFound());
  for(String tail:List.of("/updates","/updates/"+UUID.randomUUID(),"/sync","/analysis","/issues/rebuild"))mvc.perform(post("/api/games/"+ga+tail).session(b.session()).header("X-CSRF-TOKEN",b.csrf())).andExpect(status().isNotFound());
  // Encoded UUID characters must not bypass the same ownership check.
  String encoded="%"+Integer.toHexString(ga.toString().charAt(0))+ga.toString().substring(1);
  mvc.perform(get(URI.create("/api/games/"+encoded+"/overview")).session(b.session())).andExpect(status().isNotFound());
  mvc.perform(get("/api/games/"+ga+"/overview").session(a.session())).andExpect(status().isOk());
  mvc.perform(get("/api/games/"+ga+"/reviews/export").session(a.session())).andExpect(status().isOk()).andExpect(header().string("Content-Type","text/csv; charset=UTF-8"));
  mvc.perform(get("/api/games/"+ga+"/reviews/export")).andExpect(status().isUnauthorized());
 }
 @Test void legacyDataIsNotClaimedByRegistrationAndDuplicateEmailRollsBackWorkspace()throws Exception{
  UUID legacy=UUID.randomUUID();jdbc.update("INSERT INTO playersignal.game(id,steam_app_id,name) VALUES (?,999,'Legacy fixture')",legacy);String email=UUID.randomUUID()+"@example.test";var c=register(anonymous(),email);
  mvc.perform(get("/api/games/"+legacy).session(c.session())).andExpect(status().isNotFound());int count=jdbc.queryForObject("SELECT count(*) FROM playersignal.workspace",Integer.class);var next=anonymous();mvc.perform(post("/api/auth/register").session(next.session()).header("X-CSRF-TOKEN",next.csrf()).contentType("application/json").content(mapper.writeValueAsString(Map.of("email",email.toUpperCase(Locale.ROOT),"password",password,"workspaceName","Duplicate")))).andExpect(status().isConflict());assertThat(jdbc.queryForObject("SELECT count(*) FROM playersignal.workspace",Integer.class)).isEqualTo(count);
 }
 @Test void invalidCredentialsAreGenericAndOversizedPasswordsAreRejected()throws Exception{
  var c=anonymous();String email=UUID.randomUUID()+"@example.test";mvc.perform(post("/api/auth/login").session(c.session()).header("X-CSRF-TOKEN",c.csrf()).contentType("application/json").content(mapper.writeValueAsString(Map.of("email",email,"password",password)))).andExpect(status().isUnauthorized()).andExpect(content().string(org.hamcrest.Matchers.containsString("Email or password is incorrect")));
  mvc.perform(post("/api/auth/register").session(c.session()).header("X-CSRF-TOKEN",c.csrf()).contentType("application/json").content(mapper.writeValueAsString(Map.of("email",email,"password","a".repeat(73),"workspaceName","Test")))).andExpect(status().isBadRequest());
 }
}
