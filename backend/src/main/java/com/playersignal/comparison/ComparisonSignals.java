package com.playersignal.comparison;

import com.playersignal.analysis.AnalysisSettings;
import com.playersignal.issue.IssueService;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ComparisonSignals {
 public record Change(String id,String title,String category,long before,long after,Double beforeShare,Double afterShare,Double changePoints,String movement) {}
 public record Day(String date,long reviews,long recommended) {}
 public record Signals(String provider,String model,long analyzedBefore,long analyzedAfter,IssueService.Snapshot snapshot,List<Change> categories,List<Change> issues,List<Day> daily) {}
 private final JdbcTemplate jdbc;private final AnalysisSettings settings;private final IssueService issues;
 public ComparisonSignals(JdbcTemplate jdbc,AnalysisSettings settings,IssueService issues){this.jdbc=jdbc;this.settings=settings;this.issues=issues;}
 private String source(){return " FROM playersignal.review_input r JOIN playersignal.review_analysis a ON a.review_id=r.id AND a.input_hash=r.input_hash ";}
 private String eligible(){return " r.game_id=? AND a.provider=? AND a.model=? AND a.prompt_version=? AND a.status='SUCCEEDED' ";}
 private Object[] args(UUID game){return new Object[]{game,settings.provider(),settings.model(),AnalysisSettings.PROMPT_VERSION};}
 public Signals get(UUID game,LocalDate start,LocalDate pivot,LocalDate end){
  var lo=start.atStartOfDay().atOffset(ZoneOffset.UTC);var mid=pivot.atStartOfDay().atOffset(ZoneOffset.UTC);var hi=end.atStartOfDay().atOffset(ZoneOffset.UTC);
  var counts=jdbc.queryForObject("SELECT count(*) FILTER(WHERE r.created_at_steam<?),count(*) FILTER(WHERE r.created_at_steam>=?)"+source()+"WHERE "+eligible()+"AND r.created_at_steam>=? AND r.created_at_steam<?",(rs,n)->new long[]{rs.getLong(1),rs.getLong(2)},mid,mid,game,settings.provider(),settings.model(),AnalysisSettings.PROMPT_VERSION,lo,hi);
  var categories=jdbc.query("SELECT a.result->>'primaryCategory' AS category,count(*) FILTER(WHERE r.created_at_steam<?) AS before,count(*) FILTER(WHERE r.created_at_steam>=?) AS after"+source()+"WHERE "+eligible()+"AND r.created_at_steam>=? AND r.created_at_steam<? GROUP BY category ORDER BY category",(rs,n)->change(rs.getString("category"),rs.getString("category"),rs.getString("category"),rs.getLong("before"),rs.getLong("after"),counts,false),mid,mid,game,settings.provider(),settings.model(),AnalysisSettings.PROMPT_VERSION,lo,hi);
  var signals=jdbc.query("""
   SELECT c.id,c.title,c.category,
    count(*) FILTER(WHERE r.created_at_steam>=? AND r.created_at_steam<?) AS before,
    count(*) FILTER(WHERE r.created_at_steam>=? AND r.created_at_steam<?) AS after,
    min(r.created_at_steam) AS first_seen
   """+source()+" JOIN playersignal.issue_mention m ON m.analysis_id=a.id JOIN playersignal.issue_cluster c ON c.id=m.issue_id AND c.game_id=r.game_id WHERE "+eligible()+"AND r.created_at_steam<? GROUP BY c.id,c.title,c.category ORDER BY c.id",(rs,n)->change(rs.getString("id"),rs.getString("title"),rs.getString("category"),rs.getLong("before"),rs.getLong("after"),counts,!rs.getTimestamp("first_seen").toInstant().isBefore(mid.toInstant())),lo,mid,mid,hi,game,settings.provider(),settings.model(),AnalysisSettings.PROMPT_VERSION,hi).stream().filter(v->v.before()+v.after()>0).sorted(Comparator.comparingDouble((Change v)->v.changePoints()==null?0:Math.abs(v.changePoints())).reversed().thenComparing(Change::id)).toList();
  var present=jdbc.query("SELECT (created_at_steam AT TIME ZONE 'UTC')::date AS day,count(*) AS total,count(*) FILTER(WHERE voted_up) AS positive FROM playersignal.review WHERE game_id=? AND created_at_steam>=? AND created_at_steam<? GROUP BY day ORDER BY day",(rs,n)->new Day(rs.getString("day"),rs.getLong("total"),rs.getLong("positive")),game,lo,hi);
  var byDate=new HashMap<String,Day>();present.forEach(v->byDate.put(v.date(),v));var daily=new ArrayList<Day>();for(var day=start;day.isBefore(end);day=day.plusDays(1))daily.add(byDate.getOrDefault(day.toString(),new Day(day.toString(),0,0)));
  return new Signals(settings.provider(),settings.model(),counts[0],counts[1],issues.list(game,0,1).snapshot(),categories,signals,daily);
 }
 private Change change(String id,String title,String category,long before,long after,long[] counts,boolean firstAfter){
  Double b=counts[0]==0?null:(double)before/counts[0],a=counts[1]==0?null:(double)after/counts[1],delta=b==null||a==null?null:100*(a-b);
  String movement=delta==null?"INSUFFICIENT_DATA":firstAfter&&after>0?"NEW":delta>0?"GROWING":delta<0?"DECLINING":"PERSISTENT";
  return new Change(id,title,category,before,after,b,a,delta,movement);
 }
}
