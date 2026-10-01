package com.playersignal.data;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.playersignal.auth.WorkspaceContext;
import com.playersignal.shared.ApiException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service
public class DataControlService {
 public record DeleteInput(String confirmation,String password) {}
 private final JdbcTemplate jdbc;private final WorkspaceContext workspace;private final PasswordEncoder passwords;private final ObjectMapper mapper;
 public DataControlService(JdbcTemplate jdbc,WorkspaceContext workspace,PasswordEncoder passwords,ObjectMapper mapper){this.jdbc=jdbc;this.workspace=workspace;this.passwords=passwords;this.mapper=mapper;}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ) public Map<String,Object> export(){
  workspace.requireOwner();UUID id=workspace.current();long reviews=jdbc.queryForObject("SELECT count(*) FROM playersignal.review r JOIN playersignal.game g ON g.id=r.game_id WHERE g.workspace_id=?",Long.class,id);if(reviews>5000)throw new ApiException(422,"EXPORT_LIMIT","Workspace JSON export is limited to 5000 reviews. Use filtered CSV exports for larger datasets.");
  long reportBytes=jdbc.queryForObject("SELECT coalesce(sum(octet_length(r.payload::text)),0) FROM playersignal.report r JOIN playersignal.game g ON g.id=r.game_id WHERE g.workspace_id=?",Long.class,id);
  long reviewBytes=jdbc.queryForObject("SELECT coalesce(sum(octet_length((to_jsonb(r)-'raw_payload')::text)),0) FROM playersignal.review r JOIN playersignal.game g ON g.id=r.game_id WHERE g.workspace_id=?",Long.class,id);
  if(reportBytes+reviewBytes>8*1024*1024)throw new ApiException(422,"EXPORT_LIMIT","Workspace data is too large for a single JSON export. Use individual report/CSV exports.");
  var out=new LinkedHashMap<String,Object>();out.put("format","playersignal-workspace-v1");out.put("exportedAt",java.time.Instant.now().toString());out.put("workspace",jdbc.queryForMap("SELECT id,name,created_at FROM playersignal.workspace WHERE id=?",id));
  out.put("games",jdbc.queryForList("SELECT id,steam_app_id,name,created_at FROM playersignal.game WHERE workspace_id=?",id));
  out.put("reviews",jdbc.queryForList("SELECT r.id,r.game_id,r.steam_recommendation_id,r.review_text,r.language,r.voted_up,r.created_at_steam,r.updated_at_steam FROM playersignal.review r JOIN playersignal.game g ON g.id=r.game_id WHERE g.workspace_id=? ORDER BY r.id",id));
  out.put("updates",jdbc.queryForList("SELECT u.* FROM playersignal.game_update u JOIN playersignal.game g ON g.id=u.game_id WHERE g.workspace_id=?",id));
  out.put("reports",jdbc.query("SELECT r.id,r.game_id,r.type,r.created_at,r.payload::text FROM playersignal.report r JOIN playersignal.game g ON g.id=r.game_id WHERE g.workspace_id=?",(rs,n)->{try{return Map.of("id",rs.getString(1),"gameId",rs.getString(2),"type",rs.getString(3),"createdAt",rs.getTimestamp(4).toInstant().toString(),"payload",mapper.readTree(rs.getString(5)));}catch(Exception e){throw new IllegalStateException("Invalid report snapshot",e);}},id));
  out.put("savedViews",jdbc.queryForList("SELECT v.* FROM playersignal.saved_view v JOIN playersignal.game g ON g.id=v.game_id WHERE g.workspace_id=?",id));
  out.put("issueWorkflow",jdbc.queryForList("SELECT v.* FROM playersignal.issue_workflow v JOIN playersignal.game g ON g.id=v.game_id WHERE g.workspace_id=?",id));
  out.put("members",jdbc.queryForList("SELECT u.email,m.role FROM playersignal.workspace_member m JOIN playersignal.app_user u ON u.id=m.user_id WHERE m.workspace_id=?",id));
  out.put("scope","Workspace content export; excludes password hashes, invite tokens, sessions and raw reviewer payloads. Review classification history and operational logs are not included.");
  try{if(mapper.writeValueAsBytes(out).length>10*1024*1024)throw new ApiException(422,"EXPORT_LIMIT","Workspace export exceeds 10 MiB. Use filtered exports.");}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("Export serialization failed",e);}return out;
 }
 private void verify(DeleteInput input,String expected){if(input.confirmation()==null||!input.confirmation().equals(expected))throw new ApiException(400,"CONFIRMATION_REQUIRED","Enter the exact deletion confirmation.");if(workspace.enabled()){
  String hash=jdbc.queryForObject("SELECT password_hash FROM playersignal.app_user WHERE id=?",String.class,workspace.user());if(input.password()==null||input.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72||!passwords.matches(input.password(),hash))throw new ApiException(403,"REAUTH_REQUIRED","Confirm your current account password.");}}
 private void lockGame(UUID id){
  long app=jdbc.queryForObject("SELECT steam_app_id FROM playersignal.game WHERE id=? FOR UPDATE",Long.class,id);
  for(long key:new long[]{app,-app,Long.MIN_VALUE+app})if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(?)",Boolean.class,key)))throw new ApiException(409,"GAME_BUSY","Wait for this game's processing to finish before deleting it.");
 }
 @Transactional public void deleteGame(UUID game,DeleteInput input){workspace.requireOwner();UUID owner=workspace.current();if(jdbc.queryForObject("SELECT count(*) FROM playersignal.game WHERE id=? AND workspace_id=?",Long.class,game,owner)==0)throw new ApiException(404,"GAME_NOT_FOUND","Game unavailable.");verify(input,"DELETE "+game);lockGame(game);jdbc.update("DELETE FROM playersignal.game WHERE id=? AND workspace_id=?",game,owner);}
 @Transactional public void deleteAccount(DeleteInput input){
  if(!workspace.enabled())throw new ApiException(409,"LOCAL_MODE","Local mode has no account to delete.");verify(input,"DELETE MY ACCOUNT");UUID user=workspace.user();String email=workspace.principal(user).email();
  var owned=jdbc.query("SELECT workspace_id FROM playersignal.workspace_member WHERE user_id=? AND role='OWNER' ORDER BY workspace_id",(rs,n)->rs.getObject(1,UUID.class),user);
  for(UUID id:owned){
   jdbc.queryForObject("SELECT id FROM playersignal.workspace WHERE id=? FOR UPDATE",UUID.class,id);
   jdbc.queryForList("SELECT workspace_id FROM playersignal.workspace_billing WHERE workspace_id=? FOR UPDATE",id);
   if(jdbc.queryForObject("SELECT count(*) FROM playersignal.workspace_billing WHERE workspace_id=? AND (status NOT IN ('none','canceled','incomplete_expired') OR checkout_expires_at>now())",Long.class,id)>0)throw new ApiException(409,"BILLING_ACTIVE","End the workspace subscription in Stripe and wait for any checkout to expire before deleting the account.");
   jdbc.queryForObject("SELECT id FROM playersignal.workspace WHERE id=? FOR UPDATE",UUID.class,id);if(jdbc.queryForObject("SELECT count(*) FROM playersignal.workspace_member WHERE workspace_id=? AND user_id<>?",Long.class,id,user)>0)throw new ApiException(409,"WORKSPACE_HAS_MEMBERS","Remove other members from your owned workspaces before deleting your account.");
   var games=jdbc.query("SELECT id FROM playersignal.game WHERE workspace_id=? ORDER BY steam_app_id",(rs,n)->rs.getObject(1,UUID.class),id);games.forEach(this::lockGame);jdbc.update("DELETE FROM playersignal.game WHERE workspace_id=?",id);jdbc.update("DELETE FROM playersignal.usage_event WHERE workspace_id=?",id);
  }
  jdbc.update("DELETE FROM playersignal.workspace_invite WHERE email=?",email);jdbc.update("DELETE FROM playersignal.app_user WHERE id=?",user);
  for(UUID id:owned)jdbc.update("DELETE FROM playersignal.workspace WHERE id=?",id);
 }
}
