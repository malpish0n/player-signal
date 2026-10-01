package com.playersignal.usage;

import com.playersignal.shared.ApiException;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Every provider attempt is reserved durably before network IO; unknown outcomes stay charged. */
@Service
public class UsageService {
 public record Usage(String month,long analysisAttempts,long monthlyLimit,long inputTokens,long outputTokens,long unknownAttempts) {}
 private final JdbcTemplate jdbc;private final int monthlyLimit;private final int hourlySyncLimit;
 public UsageService(JdbcTemplate jdbc,@Value("${ANALYSIS_MONTHLY_ATTEMPTS:500}")int monthlyLimit,@Value("${SYNC_HOURLY_LIMIT:12}")int hourlySyncLimit){
  if(monthlyLimit<0||monthlyLimit>1000000||hourlySyncLimit<1||hourlySyncLimit>1000)throw new IllegalArgumentException("Invalid workspace usage limits");
  this.jdbc=jdbc;this.monthlyLimit=monthlyLimit;this.hourlySyncLimit=hourlySyncLimit;
 }
 public UUID workspaceForGame(UUID game){return jdbc.queryForObject("SELECT workspace_id FROM playersignal.game WHERE id=?",UUID.class,game);}
 @Transactional public UUID reserve(UUID workspace,UUID game,UUID analysis,String action,String model){
  jdbc.queryForObject("SELECT id FROM playersignal.workspace WHERE id=? FOR UPDATE",UUID.class,workspace);
  Instant now=Instant.now();Instant start;int limit;
  switch(action){case "ANALYSIS" -> {start=now.atZone(ZoneOffset.UTC).withDayOfMonth(1).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();limit=monthlyLimit;}
   case "SYNC" -> {start=now.minusSeconds(3600);limit=hourlySyncLimit;}
   case "GAME_LOOKUP" -> {start=now.minusSeconds(3600);limit=20;}
   default -> throw new IllegalArgumentException("Unknown usage action");}
  long used=jdbc.queryForObject("SELECT count(*) FROM playersignal.usage_event WHERE workspace_id=? AND action=? AND created_at>=?",Long.class,workspace,action,java.sql.Timestamp.from(start));
  if(used>=limit)throw new ApiException(429,"WORKSPACE_LIMIT",action.equals("ANALYSIS")?"Workspace monthly AI attempt limit reached. Retries count; cached and skipped reviews do not. The quota resets next UTC month.":"Workspace request limit reached. Try again later.");
  UUID id=UUID.randomUUID();jdbc.update("INSERT INTO playersignal.usage_event(id,workspace_id,game_id,analysis_id,action,model) VALUES (?,?,?,?,?,?)",id,workspace,game,analysis,action,model);return id;
 }
 public void finish(UUID id,boolean success,long input,long output){jdbc.update("UPDATE playersignal.usage_event SET status=?,input_tokens=?,output_tokens=?,finished_at=now() WHERE id=? AND status='RESERVED'",success?"SUCCEEDED":"FAILED",Math.max(0,input),Math.max(0,output),id);}
 public Usage status(UUID workspace){
  LocalDate month=LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
  return jdbc.queryForObject("SELECT count(*),coalesce(sum(input_tokens),0),coalesce(sum(output_tokens),0),count(*) FILTER(WHERE status<>'SUCCEEDED') FROM playersignal.usage_event WHERE workspace_id=? AND action='ANALYSIS' AND created_at>=?",(rs,n)->new Usage(month.toString(),rs.getLong(1),monthlyLimit,rs.getLong(2),rs.getLong(3),rs.getLong(4)),workspace,month.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime());
 }
}
