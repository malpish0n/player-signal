package com.playersignal.notification;

import com.playersignal.comparison.ComparisonService;
import com.playersignal.issue.IssueService;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Service
public class IssueAlertService {
    public record Policy(boolean enabled, Instant lastCheckedAt) {}
    public record Result(int created, String reason) {}
    private final JdbcTemplate jdbc;
    private final ComparisonService comparison;
    private final IssueService issues;
    private final TransactionTemplate transaction;
    private final boolean worker;
    public IssueAlertService(JdbcTemplate jdbc, ComparisonService comparison, IssueService issues,
            PlatformTransactionManager manager, @Value("${SCHEDULER_ENABLED:true}") boolean worker) {
        this.jdbc=jdbc; this.comparison=comparison; this.issues=issues;
        this.transaction=new TransactionTemplate(manager); this.worker=worker;
    }
    public Policy get(UUID game) {
        return jdbc.query("SELECT enabled,last_checked_at FROM playersignal.alert_policy WHERE game_id=?",
            (rs,n)->new Policy(rs.getBoolean(1),rs.getTimestamp(2)==null?null:rs.getTimestamp(2).toInstant()),game)
            .stream().findFirst().orElse(new Policy(false,null));
    }
    public Policy save(UUID game, boolean enabled) {
        jdbc.update("INSERT INTO playersignal.alert_policy(game_id,enabled) VALUES (?,?) ON CONFLICT(game_id) DO UPDATE SET enabled=EXCLUDED.enabled,updated_at=now()",game,enabled);
        return get(game);
    }
    public Result check(UUID game, boolean force) {
        return transaction.execute(status -> {
            var rows=jdbc.queryForList("SELECT enabled,last_checked_at FROM playersignal.alert_policy WHERE game_id=? FOR UPDATE",game);
            if(rows.isEmpty() || !Boolean.TRUE.equals(rows.getFirst().get("enabled"))) return new Result(0,"Alerts are disabled.");
            var checked=(java.sql.Timestamp)rows.getFirst().get("last_checked_at");
            Instant now=Instant.now();
            if(!force && checked!=null && checked.toInstant().isAfter(now.minusSeconds(3600))) return new Result(0,"Already checked this hour.");
            var date=LocalDate.now(ZoneOffset.UTC);
            var comparisonData=comparison.get(game,date.minusDays(7).toString(),7,now);
            var signals=comparisonData.signals();
            jdbc.update("UPDATE playersignal.alert_policy SET last_checked_at=? WHERE game_id=?",java.sql.Timestamp.from(now),game);
            if(signals.snapshot().stale()) return new Result(0,"Grouping is stale; rebuild issues before evaluating alerts.");
            if(signals.analyzedBefore()<20 || signals.analyzedAfter()<20) return new Result(0,"At least 20 analyzed reviews in each complete 7-day window are required.");
            int created=0;
            for(var change:signals.issues()) {
                if(change.after()<3 || change.changePoints()==null || change.changePoints()<5) continue;
                var detail=issues.detail(game,UUID.fromString(change.id()),0,1);
                if(detail.issue().metrics().severityScore()<75 || Set.of("IGNORED","RESOLVED").contains(detail.issue().status())) continue;
                // The normalized title survives most regroupings; at most one alert per issue/day.
                String key="spike:"+date+":"+UUID.nameUUIDFromBytes((change.category()+":"+com.playersignal.issue.IssueEngine.normalize(change.title())).getBytes(StandardCharsets.UTF_8));
                String message=String.format(Locale.ROOT,"%s: %d → %d mentions; %+.2f percentage points (%d / %d analyzed reviews). Compare the last two complete UTC weeks. This is an investigation signal, not proof of a regression.",change.title(),change.before(),change.after(),change.changePoints(),signals.analyzedBefore(),signals.analyzedAfter());
                created+=jdbc.update("INSERT INTO playersignal.notification(id,game_id,event_key,title,message) VALUES (?,?,?,?,?) ON CONFLICT(game_id,event_key) DO NOTHING",UUID.randomUUID(),game,key,"High-severity issue growing",message);
                if(created>=5) break;
            }
            return new Result(created,"Evaluation complete; duplicates for the same issue/day are suppressed.");
        });
    }
    @Scheduled(fixedDelayString="${ALERT_POLL_MS:3600000}")
    public void tick() {
        if(!worker)return;
        var games=jdbc.query("SELECT game_id FROM playersignal.alert_policy WHERE enabled ORDER BY last_checked_at NULLS FIRST,game_id LIMIT 100",(rs,n)->rs.getObject(1,UUID.class));
        for(UUID game:games) try {check(game,false);} catch(Exception error) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Issue alert evaluation unavailable game={} type={}",game,error.getClass().getSimpleName());
        }
    }
}
