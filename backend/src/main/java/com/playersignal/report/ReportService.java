package com.playersignal.report;
import com.fasterxml.jackson.databind.*;
import com.playersignal.comparison.*;
import com.playersignal.game.GameRepository;
import com.playersignal.issue.IssueService;
import com.playersignal.shared.ApiException;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class ReportService {
 public record Input(String type,String date,Integer days,UUID requestId) {}
 public record Summary(UUID id,String type,LocalDate periodStart,LocalDate periodEnd,Instant createdAt) {}
 public record View(Summary report,JsonNode payload) {}
 public record Excerpt(UUID issueId,String title,String sourceText,String steamReviewId,String model,String promptVersion) {}
 public record Payload(ComparisonService.Comparison comparison,List<String> summary,List<Excerpt> evidence,String method) {}
 private final JdbcTemplate jdbc;private final ObjectMapper mapper;private final GameRepository games;private final ComparisonService comparison;private final IssueService issues;
 public ReportService(JdbcTemplate jdbc,ObjectMapper mapper,GameRepository games,ComparisonService comparison,IssueService issues){this.jdbc=jdbc;this.mapper=mapper;this.games=games;this.comparison=comparison;this.issues=issues;}
 public List<Summary> list(UUID game){games.get(game);return jdbc.query("SELECT * FROM playersignal.report WHERE game_id=? ORDER BY created_at DESC,id",this::summary,game);}
 public View get(UUID game,UUID id){games.get(game);return jdbc.query("SELECT * FROM playersignal.report WHERE game_id=? AND id=?",(rs,n)->new View(summary(rs,n),tree(rs.getString("payload"))),game,id).stream().findFirst().orElseThrow(()->new ApiException(404,"REPORT_NOT_FOUND","Report not found in this game."));}
 @Transactional(isolation=Isolation.SERIALIZABLE)
 public View create(UUID game,Input input){
  games.get(game);if(input.requestId()==null||input.type()==null||!Set.of("WEEKLY","PATCH").contains(input.type()))throw new ApiException(400,"INVALID_REPORT","Choose WEEKLY or PATCH and include a requestId UUID.");
  jdbc.queryForObject("SELECT id FROM playersignal.game WHERE id=? FOR UPDATE",UUID.class,game);
  String requestKey=input.type()+":"+input.date()+":"+input.days();
  var existing=jdbc.query("SELECT id,request_key FROM playersignal.report WHERE game_id=? AND request_id=?",(rs,n)->Map.entry(rs.getObject("id",UUID.class),rs.getString("request_key")),game,input.requestId());
  if(!existing.isEmpty()){if(!existing.getFirst().getValue().equals(requestKey))throw new ApiException(409,"REQUEST_REUSED","Use a new requestId for a different report.");return get(game,existing.getFirst().getKey());}
  if(jdbc.queryForObject("SELECT count(*) FROM playersignal.report WHERE game_id=?",Long.class,game)>=100)throw new ApiException(422,"REPORT_LIMIT","This game has reached its 100-report history limit.");
  Instant now=Instant.now();String date=input.type().equals("WEEKLY")?LocalDate.now(ZoneOffset.UTC).minusDays(7).toString():input.date();int days=input.type().equals("WEEKLY")?7:input.days()==null?7:input.days();
  var data=comparison.get(game,date,days,now);var summary=new ArrayList<String>();var evidence=new ArrayList<Excerpt>();
  if(data.signals().snapshot().stale())summary.add("Issue grouping is missing or stale. Rebuild grouping before interpreting issue changes.");
  for(var change:data.signals().issues().stream().limit(5).toList()){
   summary.add(change.title()+": "+change.before()+" → "+change.after()+" mentions; "+(change.changePoints()==null?"no comparison baseline":String.format(Locale.ROOT,"%+.2f percentage points of analyzed reviews",change.changePoints()))+" ("+change.movement()+"). Investigate the supporting reviews; this is not proof of causation.");
   var detail=issues.detail(game,UUID.fromString(change.id()),0,1);
   for(var quote:detail.evidence())evidence.add(new Excerpt(detail.issue().id(),detail.issue().title(),quote.sourceText(),quote.steamRecommendationId(),quote.model(),quote.promptVersion()));
  }
  if(data.signals().issues().isEmpty())summary.add("No current grouped issue evidence is available in these windows. This is not evidence that the game has no problems.");
  var payload=new Payload(data,summary,evidence,"Deterministic evidence brief; not an AI-generated executive assessment.");
  String json;try{json=mapper.writeValueAsString(payload);}catch(Exception e){throw new IllegalStateException("Report serialization failed",e);}
  if(json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>2000000)throw new ApiException(422,"REPORT_TOO_LARGE","This report exceeds the snapshot size limit.");
  UUID id=UUID.randomUUID();jdbc.update("INSERT INTO playersignal.report(id,game_id,request_id,request_key,type,period_start,period_end,payload) VALUES (?,?,?,?,?,?,?,?::jsonb)",id,game,input.requestId(),requestKey,input.type(),data.after().from(),data.after().to(),json);return get(game,id);
 }
 private Summary summary(java.sql.ResultSet rs,int n)throws java.sql.SQLException{return new Summary(rs.getObject("id",UUID.class),rs.getString("type"),rs.getObject("period_start",LocalDate.class),rs.getObject("period_end",LocalDate.class),rs.getTimestamp("created_at").toInstant());}
 private JsonNode tree(String json){try{return mapper.readTree(json);}catch(Exception e){throw new IllegalStateException("Invalid stored report",e);}}
}
