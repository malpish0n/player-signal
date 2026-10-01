package com.playersignal.report;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.playersignal.auth.WorkspaceContext;
import com.playersignal.shared.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportShareService {
    public record Input(int days, String confirmation) {}
    public record State(boolean active, Instant expiresAt) {}
    public record Created(String token, Instant expiresAt) {}
    private final JdbcTemplate jdbc;
    private final WorkspaceContext workspace;
    private final ReportService reports;
    private final ObjectMapper mapper;
    private long window;
    private int reads;
    public ReportShareService(JdbcTemplate jdbc,WorkspaceContext workspace,ReportService reports,ObjectMapper mapper) {
        this.jdbc=jdbc;this.workspace=workspace;this.reports=reports;this.mapper=mapper;
    }
    public State state(UUID game,UUID report) {
        workspace.requireOwner();reports.get(game,report);
        return jdbc.query("SELECT expires_at,revoked_at FROM playersignal.report_share WHERE report_id=?",(rs,n)->
            new State(rs.getTimestamp(2)==null && rs.getTimestamp(1).toInstant().isAfter(Instant.now()),rs.getTimestamp(1).toInstant()),report)
            .stream().findFirst().orElse(new State(false,null));
    }
    @Transactional public Created create(UUID game,UUID report,Input input) {
        workspace.requireOwner();reports.get(game,report);
        if(!Set.of(1,7,30).contains(input.days()) || !"SHARE REPORT".equals(input.confirmation()))
            throw new ApiException(400,"SHARE_CONFIRMATION","Confirm sharing this report and choose 1, 7 or 30 days.");
        byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expires=Instant.now().plusSeconds(input.days()*86400L);
        jdbc.update("""
            INSERT INTO playersignal.report_share(report_id,token_hash,expires_at) VALUES (?,?,?)
            ON CONFLICT(report_id) DO UPDATE SET token_hash=EXCLUDED.token_hash,expires_at=EXCLUDED.expires_at,revoked_at=NULL,created_at=now()
            """,report,hash(token),java.sql.Timestamp.from(expires));
        return new Created(token,expires);
    }
    public State revoke(UUID game,UUID report) {
        workspace.requireOwner();reports.get(game,report);
        jdbc.update("UPDATE playersignal.report_share SET revoked_at=now() WHERE report_id=?",report);
        return state(game,report);
    }
    private synchronized void limit() {
        long minute=System.currentTimeMillis()/60000;
        if(window!=minute){window=minute;reads=0;}
        if(++reads>120)throw new ApiException(429,"SHARE_RATE_LIMIT","Shared reports are busy. Retry in a minute.");
    }
    public ObjectNode publicReport(String token) {
        if(!token.matches("[A-Za-z0-9_-]{43}"))throw missing();
        limit();
        var rows=jdbc.query("""
            SELECT r.type,r.period_start,r.period_end,r.created_at,r.payload
            FROM playersignal.report_share s JOIN playersignal.report r ON r.id=s.report_id
            WHERE s.token_hash=? AND s.revoked_at IS NULL AND s.expires_at>now()
            """,(rs,n)-> {
                JsonNode payload;
                try {payload=mapper.readTree(rs.getString("payload"));}catch(Exception e){throw new IllegalStateException("Invalid report snapshot",e);}
                // An explicit projection prevents identifiers, memberships or new private payload fields leaking.
                ObjectNode out=mapper.createObjectNode();
                out.put("gameName",payload.path("comparison").path("game").path("name").asText());
                out.put("type",rs.getString("type"));out.put("periodStart",rs.getString("period_start"));out.put("periodEnd",rs.getString("period_end"));
                out.put("createdAt",rs.getTimestamp("created_at").toInstant().toString());out.set("summary",payload.path("summary"));out.set("method",payload.path("method"));
                var evidence=out.putArray("evidence");
                for(var value:payload.path("evidence")) {
                    var item=evidence.addObject();
                    for(String field:List.of("title","sourceText","steamReviewId","model","promptVersion"))item.set(field,value.path(field));
                }
                return out;
            },hash(token));
        return rows.stream().findFirst().orElseThrow(this::missing);
    }
    private ApiException missing(){return new ApiException(404,"SHARE_NOT_FOUND","This report link is unavailable, expired or revoked.");}
    private String hash(String token){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException error){throw new IllegalStateException(error);}}
}
