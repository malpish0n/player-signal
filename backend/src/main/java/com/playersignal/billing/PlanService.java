package com.playersignal.billing;

import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PlanService {
    public record Limits(String plan,int games,int monthlyAttempts,int reportsPerGame,int seats) {}
    private final JdbcTemplate jdbc;private final boolean enabled;private final int localAttempts;
    public PlanService(JdbcTemplate jdbc,@Value("${STRIPE_ENABLED:false}")boolean enabled,@Value("${ANALYSIS_MONTHLY_ATTEMPTS:500}")int localAttempts){this.jdbc=jdbc;this.enabled=enabled;this.localAttempts=localAttempts;}
    public boolean enabled(){return enabled;}
    public Limits limits(UUID workspace){
        if(!enabled)return new Limits("LOCAL",10000,localAttempts,100,50);
        var rows=jdbc.queryForList("SELECT plan,status,period_end,grace_until,verified_at FROM playersignal.workspace_billing WHERE workspace_id=?",workspace);
        if(rows.isEmpty())return forPlan("FREE");
        var row=rows.getFirst();Instant now=Instant.now();String status=(String)row.get("status");
        boolean fresh=row.get("verified_at") instanceof java.sql.Timestamp t&&t.toInstant().isAfter(now.minusSeconds(48*3600));
        Object until=status.equals("past_due")?row.get("grace_until"):row.get("period_end");
        boolean valid=Set.of("active","trialing","past_due").contains(status)&&until instanceof java.sql.Timestamp t&&t.toInstant().isAfter(now)&&fresh;
        return forPlan(valid?(String)row.get("plan"):"FREE");
    }
    public Limits forPlan(String plan){return switch(plan){case "INDIE"->new Limits(plan,3,5000,50,3);case "STUDIO"->new Limits(plan,10,20000,100,10);default->new Limits("FREE",1,500,10,1);};}
}
