package com.playersignal.issue;
import com.playersignal.comparison.*;
import com.playersignal.analysis.AnalysisSettings;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/games/{game}/issues/{issue}")
public class IssueInsightsController {
 public record Day(String date,long mentions) {}
 public record Trend(int days,long analyzedBefore,long analyzedAfter,ComparisonSignals.Change change,List<Day> daily,IssueService.Snapshot snapshot,double priorityScore,Map<String,Double> components) {}
 private final IssueService issues;private final ComparisonService comparison;private final JdbcTemplate jdbc;private final AnalysisSettings settings;private final IssueWorkflow workflow;
 public IssueInsightsController(IssueService issues,ComparisonService comparison,JdbcTemplate jdbc,AnalysisSettings settings,IssueWorkflow workflow){this.issues=issues;this.comparison=comparison;this.jdbc=jdbc;this.settings=settings;this.workflow=workflow;}
 @GetMapping("/workflow") public IssueWorkflow.State state(@PathVariable UUID game,@PathVariable UUID issue){return workflow.get(game,issue);}
 @PostMapping("/workflow") public IssueWorkflow.State state(@PathVariable UUID game,@PathVariable UUID issue,@RequestBody IssueWorkflow.State input){return workflow.save(game,issue,input);}
 @GetMapping("/trend") @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public Trend trend(@PathVariable UUID game,@PathVariable UUID issue,@RequestParam(defaultValue="30")int days){
  var detail=issues.detail(game,issue,0,1);var now=Instant.now();var boundary=LocalDate.now(ZoneOffset.UTC).minusDays(days);var c=comparison.get(game,boundary.toString(),days,now);var s=c.signals();
  var change=s.issues().stream().filter(v->v.id().equals(issue.toString())).findFirst().orElse(null);
  var present=jdbc.query("""
   SELECT (r.created_at_steam AT TIME ZONE 'UTC')::date AS day,count(*)
   FROM playersignal.issue_mention m JOIN playersignal.review_analysis a ON a.id=m.analysis_id JOIN playersignal.review_input r ON r.id=a.review_id AND r.input_hash=a.input_hash
   WHERE m.issue_id=? AND r.game_id=? AND a.provider=? AND a.model=? AND a.prompt_version=? AND a.status='SUCCEEDED' AND r.created_at_steam>=? AND r.created_at_steam<? GROUP BY day
   """,(rs,n)->new Day(rs.getString(1),rs.getLong(2)),issue,game,settings.provider(),settings.model(),AnalysisSettings.PROMPT_VERSION,c.before().from().atStartOfDay().atOffset(ZoneOffset.UTC),c.after().to().plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC));
  var map=new HashMap<String,Long>();present.forEach(v->map.put(v.date(),v.mentions()));var daily=s.daily().stream().map(v->new Day(v.date(),map.getOrDefault(v.date(),0L))).toList();
  var m=detail.issue().metrics();double share=change==null||change.afterShare()==null?0:change.afterShare(),velocity=change==null||change.changePoints()==null?0:Math.max(0,change.changePoints()/100),recency=Math.max(0,1-Duration.between(m.lastSeenAt(),now).toDays()/30.0);
  var parts=Map.of("mentionShare",35*share,"positiveShareVelocity",25*velocity,"negativeRatio",20*m.negativeRatio(),"modelSeverity",.1*m.severityScore(),"recency",10*Math.min(1,recency));
  double score=change==null||change.after()==0?0:Math.min(100,parts.values().stream().mapToDouble(Double::doubleValue).sum());
  return new Trend(days,s.analyzedBefore(),s.analyzedAfter(),change,daily,s.snapshot(),score,parts);
 }
}
