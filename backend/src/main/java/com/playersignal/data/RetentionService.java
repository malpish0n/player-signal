package com.playersignal.data;

import com.playersignal.shared.ApiException;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class RetentionService {
    public record Input(boolean enabled,int reviewDays,int reportDays,int notificationDays,String confirmation) {}
    public record Counts(long reviews,long reports,long readNotifications) {}
    public record Policy(boolean enabled,int reviewDays,int reportDays,int notificationDays,Instant lastRunAt,boolean workerEnabled) {}
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final boolean worker;
    public RetentionService(JdbcTemplate jdbc,PlatformTransactionManager manager,@Value("${SCHEDULER_ENABLED:true}")boolean worker){this.jdbc=jdbc;this.transaction=new TransactionTemplate(manager);this.worker=worker;}
    public Policy get(UUID game){return jdbc.query("SELECT * FROM playersignal.retention_policy WHERE game_id=?",(rs,n)->new Policy(rs.getBoolean("enabled"),rs.getInt("review_days"),rs.getInt("report_days"),rs.getInt("notification_days"),rs.getTimestamp("last_run_at")==null?null:rs.getTimestamp("last_run_at").toInstant(),worker),game).stream().findFirst().orElse(new Policy(false,0,0,90,null,worker));}
    private void validate(Input input){if(!Set.of(0,90,180,365).contains(input.reviewDays())||!Set.of(0,30,90,180,365).contains(input.reportDays())||!Set.of(30,90,180,365).contains(input.notificationDays()))throw new ApiException(400,"INVALID_RETENTION","Choose one of the supported retention periods.");}
    private Object cutoff(int days,Instant now){return java.sql.Timestamp.from(now.minusSeconds(days*86400L));}
    public Counts preview(UUID game,Input input){validate(input);return counts(game,input,Instant.now());}
    private Counts counts(UUID game,Input input,Instant now){return new Counts(input.reviewDays()==0?0:jdbc.queryForObject("SELECT count(*) FROM playersignal.review WHERE game_id=? AND created_at_steam<?",Long.class,game,cutoff(input.reviewDays(),now)),input.reportDays()==0?0:jdbc.queryForObject("SELECT count(*) FROM playersignal.report WHERE game_id=? AND created_at<?",Long.class,game,cutoff(input.reportDays(),now)),jdbc.queryForObject("SELECT count(*) FROM playersignal.notification WHERE game_id=? AND read_at IS NOT NULL AND created_at<?",Long.class,game,cutoff(input.notificationDays(),now)));}
    public Policy save(UUID game,Input input){validate(input);if(input.enabled()&&!"ENABLE RETENTION".equals(input.confirmation()))throw new ApiException(400,"RETENTION_CONFIRMATION","Confirm automatic deletion before enabling retention.");
        return transaction.execute(status->{
            jdbc.queryForObject("SELECT id FROM playersignal.game WHERE id=? FOR UPDATE",UUID.class,game);
            jdbc.update("""
                INSERT INTO playersignal.retention_policy(game_id,enabled,review_days,report_days,notification_days) VALUES (?,?,?,?,?)
                ON CONFLICT(game_id) DO UPDATE SET enabled=EXCLUDED.enabled,review_days=EXCLUDED.review_days,report_days=EXCLUDED.report_days,notification_days=EXCLUDED.notification_days,updated_at=now()
                """,game,input.enabled(),input.reviewDays(),input.reportDays(),input.notificationDays());return get(game);
        });
    }
    private void prune(UUID game){transaction.executeWithoutResult(status->{
        var games=jdbc.query("SELECT steam_app_id FROM playersignal.game WHERE id=? FOR UPDATE",(rs,n)->rs.getLong(1),game);
        if(games.isEmpty())return;
        var policy=get(game);Instant now=Instant.now();
        if(!policy.enabled()||(policy.lastRunAt()!=null&&policy.lastRunAt().isAfter(now.minusSeconds(86400))))return;
        long app=games.getFirst();
        // Use the same locks as import, analysis and regrouping; never delete their source rows mid-job.
        for(long key:new long[]{app,-app,Long.MIN_VALUE+app})if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(?)",Boolean.class,key)))return;
        var input=new Input(true,policy.reviewDays(),policy.reportDays(),policy.notificationDays(),null);
        var count=counts(game,input,now);
        if(count.reviews()>0){
            jdbc.update("DELETE FROM playersignal.issue_cluster WHERE game_id=?",game);
            jdbc.update("DELETE FROM playersignal.issue_snapshot WHERE game_id=?",game);
            jdbc.update("DELETE FROM playersignal.review WHERE game_id=? AND created_at_steam<?",game,cutoff(policy.reviewDays(),now));
        }
        if(policy.reportDays()>0)jdbc.update("DELETE FROM playersignal.report WHERE game_id=? AND created_at<?",game,cutoff(policy.reportDays(),now));
        jdbc.update("DELETE FROM playersignal.notification WHERE game_id=? AND read_at IS NOT NULL AND created_at<?",game,cutoff(policy.notificationDays(),now));
        jdbc.update("UPDATE playersignal.retention_policy SET last_run_at=? WHERE game_id=?",java.sql.Timestamp.from(now),game);
    });}
    @Scheduled(fixedDelayString="${RETENTION_POLL_MS:3600000}") public void tick(){if(!worker)return;
        for(UUID game:jdbc.query("SELECT game_id FROM playersignal.retention_policy WHERE enabled ORDER BY last_run_at NULLS FIRST,game_id LIMIT 50",(rs,n)->rs.getObject(1,UUID.class)))try{prune(game);}catch(Exception error){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Retention unavailable game={} type={}",game,error.getClass().getSimpleName());}
    }
}
