package com.playersignal.overview;

import com.playersignal.analysis.*;
import com.playersignal.game.GameRepository;
import com.playersignal.issue.*;
import com.playersignal.shared.ApiException;
import com.playersignal.steam.IngestionRepository;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class OverviewService {
    public record Day(String date,long recommended,long notRecommended) {}
    public record Category(String category,long count) {}
    public record Metrics(long reviews,long recommended,Double positiveRatio,long previousReviews,Double previousPositiveRatio,Double reviewChangePercent,Double positiveChangePoints) {}
    public record Overview(String mode,GameRepository.Game game,int days,Instant periodStart,Instant asOf,long imported,
                           Metrics metrics,List<Day> daily,List<Category> categories,AnalysisService.Status analysis,
                           IngestionRepository.Run ingestion,IssueService.Page issues,long highSeverityIssues) {}
    private final JdbcTemplate jdbc;private final GameRepository games;private final AnalysisSettings settings;private final AnalysisService analysis;private final IngestionRepository ingestion;private final IssueService issues;
    public OverviewService(JdbcTemplate jdbc,GameRepository games,AnalysisSettings settings,AnalysisService analysis,IngestionRepository ingestion,IssueService issues){this.jdbc=jdbc;this.games=games;this.settings=settings;this.analysis=analysis;this.ingestion=ingestion;this.issues=issues;}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Overview get(UUID game,int days,Instant now) {
        validateDays(days);var selected=games.get(game);Instant start=now.minus(days,ChronoUnit.DAYS),previous=start.minus(days,ChronoUnit.DAYS);
        var metrics=jdbc.queryForObject("""
            SELECT count(*) FILTER(WHERE created_at_steam>=?) AS current_count,
              count(*) FILTER(WHERE created_at_steam>=? AND voted_up) AS current_positive,
              count(*) FILTER(WHERE created_at_steam<?) AS previous_count,
              count(*) FILTER(WHERE created_at_steam<? AND voted_up) AS previous_positive
            FROM playersignal.review WHERE game_id=? AND created_at_steam>=? AND created_at_steam<?
            """,(rs,n)->metrics(rs.getLong(1),rs.getLong(2),rs.getLong(3),rs.getLong(4)),Timestamp.from(start),Timestamp.from(start),Timestamp.from(start),Timestamp.from(start),game,Timestamp.from(previous),Timestamp.from(now));
        var present=jdbc.query("""
            SELECT (created_at_steam AT TIME ZONE 'UTC')::date AS day,count(*) FILTER(WHERE voted_up) AS positive,count(*) FILTER(WHERE NOT voted_up) AS negative
            FROM playersignal.review WHERE game_id=? AND created_at_steam>=? AND created_at_steam<? GROUP BY day ORDER BY day
            """,(rs,n)->new Day(rs.getString("day"),rs.getLong("positive"),rs.getLong("negative")),game,Timestamp.from(start),Timestamp.from(now));
        var categories=jdbc.query("""
            SELECT a.result->>'primaryCategory' AS category,count(*) AS n FROM playersignal.review_input r
            JOIN playersignal.review_analysis a ON a.review_id=r.id AND a.input_hash=r.input_hash
            WHERE r.game_id=? AND r.created_at_steam>=? AND r.created_at_steam<? AND a.status='SUCCEEDED' AND a.provider=? AND a.model=? AND a.prompt_version=?
            GROUP BY category ORDER BY n DESC,category
            """,(rs,n)->new Category(rs.getString("category"),rs.getLong("n")),game,Timestamp.from(start),Timestamp.from(now),AnalysisSettings.PROVIDER,settings.model(),AnalysisSettings.PROMPT_VERSION);
        var status=analysis.status(game);var issuePage=issues.list(game,0,5);
        long high=jdbc.queryForObject("SELECT count(*) FROM playersignal.issue_cluster WHERE game_id=? AND (metrics->>'severityScore')::double precision>=75",Long.class,game);
        return new Overview("LIVE",selected,days,start,now,status.counts().total(),metrics,fillDays(start,now,present),categories,status,ingestion.latest(game),issuePage,high);
    }
    public static void validateDays(int days){if(days!=7&&days!=30&&days!=90)throw new ApiException(400,"INVALID_RANGE","Choose 7, 30 or 90 days.");}
    public static Metrics metrics(long count,long positive,long prior,long priorPositive){Double ratio=count==0?null:(double)positive/count,old=prior==0?null:(double)priorPositive/prior;return new Metrics(count,positive,ratio,prior,old,prior==0?null:100.0*(count-prior)/prior,ratio==null||old==null?null:100*(ratio-old));}
    public static List<Day> fillDays(Instant start,Instant end,List<Day> present){Map<String,Day> map=new HashMap<>();present.forEach(d->map.put(d.date(),d));var days=new ArrayList<Day>();for(LocalDate day=start.atZone(ZoneOffset.UTC).toLocalDate();!day.isAfter(end.atZone(ZoneOffset.UTC).toLocalDate());day=day.plusDays(1))days.add(map.getOrDefault(day.toString(),new Day(day.toString(),0,0)));return days;}
    public static Overview demo(int days){
        validateDays(days);var now=IssueDemo.NOW;var start=now.minus(days,ChronoUnit.DAYS);var previous=start.minus(days,ChronoUnit.DAYS);var sources=IssueDemo.corpus();
        var dates=new ArrayList<Instant>();var votes=new ArrayList<Boolean>();sources.forEach(s->{dates.add(s.seenAt());votes.add(false);});
        for(int i=0;i<4;i++){dates.add(now.minus(3+i,ChronoUnit.DAYS));votes.add(true);}
        long count=0,positive=0,prior=0,priorPositive=0;var dayMap=new TreeMap<String,long[]>();var categoryMap=new TreeMap<String,Long>();
        for(int i=0;i<dates.size();i++){Instant date=dates.get(i);if(!date.isBefore(start)&&date.isBefore(now)){count++;if(votes.get(i))positive++;long[] day=dayMap.computeIfAbsent(date.atZone(ZoneOffset.UTC).toLocalDate().toString(),k->new long[2]);day[votes.get(i)?0:1]++;categoryMap.merge(i<6?sources.get(i).classification().primaryCategory().name():"POSITIVE",1L,Long::sum);}else if(!date.isBefore(previous)&&date.isBefore(start)){prior++;if(votes.get(i))priorPositive++;}}
        var clusters=IssueDemo.create().issues().stream().map(IssueDemo.DemoIssue::issue).toList();var page=new IssueService.Page(new IssueService.Snapshot(now,IssueEngine.VERSION,IssueEngine.THRESHOLD,6,6,false),clusters,0,5,clusters.size());
        var status=new AnalysisService.Status(false,"synthetic","fixture-v1","synthetic-v1",0,new AnalysisRepository.Counts(10,0,0,10,0,0),null);
        return new Overview("SYNTHETIC_DEMO",new GameRepository.Game(new UUID(2,1),0,"Example game",null,now),days,start,now,10,metrics(count,positive,prior,priorPositive),fillDays(start,now,dayMap.entrySet().stream().map(e->new Day(e.getKey(),e.getValue()[0],e.getValue()[1])).toList()),categoryMap.entrySet().stream().map(e->new Category(e.getKey(),e.getValue())).toList(),status,null,page,3);
    }
}
