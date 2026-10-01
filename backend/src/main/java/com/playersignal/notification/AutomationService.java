package com.playersignal.notification;
import com.playersignal.analysis.*;
import com.playersignal.issue.IssueService;
import com.playersignal.steam.*;
import com.playersignal.report.ReportService;
import com.playersignal.shared.ApiException;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Service;

@Service @EnableScheduling
public class AutomationService {
 public record Policy(UUID gameId,boolean enabled,int intervalHours,boolean analyzeLocal,boolean weeklyReport,String phase,Instant nextSyncAt,String lastError,boolean workerEnabled) {}
 public record Input(boolean enabled,int intervalHours,boolean analyzeLocal,boolean weeklyReport) {}
 private final JdbcTemplate jdbc;private final DataSource source;private final IngestionService imports;private final IngestionRepository runs;private final AnalysisService analysis;private final AnalysisRepository analyses;private final AnalysisSettings settings;private final IssueService issues;private final ReportService reports;private final boolean enabled;
 public AutomationService(JdbcTemplate jdbc,DataSource source,IngestionService imports,IngestionRepository runs,AnalysisService analysis,AnalysisRepository analyses,AnalysisSettings settings,IssueService issues,ReportService reports,@Value("${SCHEDULER_ENABLED:true}")boolean enabled){this.jdbc=jdbc;this.source=source;this.imports=imports;this.runs=runs;this.analysis=analysis;this.analyses=analyses;this.settings=settings;this.issues=issues;this.reports=reports;this.enabled=enabled;}
 public Policy get(UUID game){return jdbc.query("SELECT * FROM playersignal.game_automation WHERE game_id=?",(rs,n)->new Policy(game,rs.getBoolean("enabled"),rs.getInt("interval_hours"),rs.getBoolean("analyze_local"),rs.getBoolean("weekly_report"),rs.getString("phase"),rs.getTimestamp("next_sync_at").toInstant(),rs.getString("last_error"),enabled),game).stream().findFirst().orElse(new Policy(game,false,24,false,false,"IDLE",null,null,enabled));}
 @org.springframework.transaction.annotation.Transactional public Policy save(UUID game,Input input){if(input.intervalHours()<1||input.intervalHours()>168)throw new ApiException(400,"INVALID_SCHEDULE","Choose an interval of 1–168 hours.");if(input.analyzeLocal()&&!Set.of("local","ollama").contains(settings.mode()))throw new ApiException(400,"LOCAL_ANALYSIS_REQUIRED","Automatic analysis requires local rules or a local Ollama model. Cloud providers are never scheduled.");
  jdbc.queryForObject("SELECT pg_advisory_xact_lock(81243,1)",Object.class);
  jdbc.update("""
   INSERT INTO playersignal.game_automation(game_id,enabled,interval_hours,analyze_local,weekly_report) VALUES (?,?,?,?,?)
   ON CONFLICT(game_id) DO UPDATE SET enabled=EXCLUDED.enabled,interval_hours=EXCLUDED.interval_hours,analyze_local=EXCLUDED.analyze_local,weekly_report=EXCLUDED.weekly_report,
   phase=CASE WHEN NOT EXCLUDED.enabled THEN 'IDLE' ELSE playersignal.game_automation.phase END,
   run_id=CASE WHEN NOT EXCLUDED.enabled THEN NULL ELSE playersignal.game_automation.run_id END,updated_at=now()
   """,game,input.enabled(),input.intervalHours(),input.analyzeLocal(),input.weeklyReport());return get(game);
 }
 public List<Map<String,Object>> notifications(UUID game){return jdbc.queryForList("SELECT id,title,message,created_at,read_at FROM playersignal.notification WHERE game_id=? ORDER BY created_at DESC,id LIMIT 100",game);}
 public void read(UUID game){jdbc.update("UPDATE playersignal.notification SET read_at=now() WHERE game_id=? AND read_at IS NULL",game);}
 private void notify(UUID game,String key,String title,String message){jdbc.update("INSERT INTO playersignal.notification(id,game_id,event_key,title,message) VALUES (?,?,?,?,?) ON CONFLICT(game_id,event_key) DO NOTHING",UUID.randomUUID(),game,key,title,message);}
 private void phase(UUID game,String phase,UUID run){jdbc.update("UPDATE playersignal.game_automation SET phase=?,run_id=?,last_error=NULL,updated_at=now() WHERE game_id=?",phase,run,game);}
 @Scheduled(fixedDelayString="${SCHEDULER_POLL_MS:60000}") public void tick(){
  if(!enabled)return;
  // One dispatcher across application instances. Workers keep their existing per-game locks.
  try(var connection=source.getConnection()){
   try(var statement=connection.prepareStatement("SELECT pg_try_advisory_lock(81243,1)")){var result=statement.executeQuery();result.next();if(!result.getBoolean(1))return;}
   try{for(var row:jdbc.queryForList("SELECT * FROM playersignal.game_automation WHERE enabled ORDER BY next_sync_at,game_id LIMIT 100")){UUID game=(UUID)row.get("game_id");try{advance(game,row);}catch(Exception error){if(error instanceof ApiException a&&a.status()==409)continue;String text=error instanceof ApiException?error.getMessage():"Scheduled task failed. Inspect processing and retry manually.";jdbc.update("UPDATE playersignal.game_automation SET phase='IDLE',run_id=NULL,last_error=?,next_sync_at=now()+interval '1 hour',next_report_at=now()+interval '1 hour' WHERE game_id=?",text,game);notify(game,"failure:"+Instant.now().atZone(ZoneOffset.UTC).toLocalDate()+":"+error.getClass().getSimpleName(),"Scheduled work needs attention",text);}}}
   finally{try(var statement=connection.prepareStatement("SELECT pg_advisory_unlock(81243,1)")){statement.execute();}}
  }catch(Exception e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Scheduled dispatcher unavailable type={}",e.getClass().getSimpleName());}
 }
 private void advance(UUID game,Map<String,Object> row){
  String current=(String)row.get("phase");UUID run=(UUID)row.get("run_id");boolean local=(Boolean)row.get("analyze_local");
  if(current.equals("FETCHING")){
   // Persisted run ID prevents a later unrelated run from advancing this pipeline.
   var status=jdbc.queryForMap("SELECT status,error FROM playersignal.ingestion_run WHERE id=? AND game_id=?",run,game);if(status.get("status").equals("RUNNING"))return;
   if(status.get("status").equals("FAILED")){notify(game,"sync:"+run,"Import failed",Objects.toString(status.get("error"),"Open Processing for details."));phase(game,"IDLE",null);return;}
   if(local&&Set.of("local","ollama").contains(settings.mode())){var started=analysis.start(game,false);phase(game,"ANALYZING",started.id());}else phase(game,"IDLE",null);return;
  }
  if(current.equals("ANALYZING")){var result=analyses.getRun(run);if(result.status().equals("RUNNING"))return;if(result.failed()>0||result.status().equals("FAILED"))notify(game,"analysis:"+run,"Analysis needs review",Objects.toString(result.error(),"Some reviews could not be analyzed. Open Processing."));phase(game,"GROUPING",null);return;}
  if(current.equals("GROUPING")){issues.rebuild(game);phase(game,"IDLE",null);return;}
  Instant now=Instant.now();
  if((Boolean)row.get("weekly_report")&&!((java.sql.Timestamp)row.get("next_report_at")).toInstant().isAfter(now)){
   String week=LocalDate.now(ZoneOffset.UTC).with(java.time.DayOfWeek.MONDAY).toString();UUID request=UUID.nameUUIDFromBytes((game+":weekly:"+week).getBytes(java.nio.charset.StandardCharsets.UTF_8));var report=reports.create(game,new ReportService.Input("WEEKLY",null,null,request));jdbc.update("UPDATE playersignal.game_automation SET next_report_at=now()+interval '7 days' WHERE game_id=?",game);notify(game,"report:"+report.report().id(),"Weekly report ready","Open Reports to inspect the saved evidence brief.");
  }
  if(!((java.sql.Timestamp)row.get("next_sync_at")).toInstant().isAfter(now)){
   var started=imports.start(game);jdbc.update("UPDATE playersignal.game_automation SET next_sync_at=now()+interval '1 hour'*interval_hours WHERE game_id=?",game);phase(game,"FETCHING",started.id());
  }
 }
}
