package com.playersignal.issue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.playersignal.analysis.*;
import com.playersignal.game.GameRepository;
import com.playersignal.shared.*;
import java.time.Instant;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class IssueService {
    public record Summary(UUID id,String title,String category,String status,IssueEngine.Metrics metrics) {}
    public record Snapshot(Instant builtAt,String algorithm,double threshold,int clusteredReviews,long eligibleReviews,boolean stale) {}
    public record Page(Snapshot snapshot,List<Summary> items,int page,int size,long total) {}
    public record Evidence(UUID analysisId,UUID reviewId,String steamRecommendationId,String sourceText,String language,Instant seenAt,
                           Classification classification,double similarity,String model,String promptVersion) {}
    public record Detail(Snapshot snapshot,Summary issue,List<Evidence> evidence,int page,int size,long total) {}
    private record InputState(long count,String fingerprint) {}
    private final IssueEmbeddingService embeddings;
    private final IssueWorkflow workflow;
    private final JdbcTemplate jdbc;private final DataSource dataSource;private final ObjectMapper mapper;private final AnalysisSettings settings;private final GameRepository games;
    public IssueService(JdbcTemplate jdbc,DataSource dataSource,ObjectMapper mapper,AnalysisSettings settings,GameRepository games,IssueWorkflow workflow,IssueEmbeddingService embeddings) {this.embeddings=embeddings;this.workflow=workflow;this.jdbc=jdbc;this.dataSource=dataSource;this.mapper=mapper;this.settings=settings;this.games=games;}
    private String eligible() {return """
        FROM playersignal.review_input r JOIN playersignal.review_analysis a ON a.review_id=r.id AND a.input_hash=r.input_hash
        WHERE r.game_id=? AND a.provider=? AND a.model=? AND a.prompt_version=? AND a.status='SUCCEEDED'
          AND a.result->>'isActionable'='true' AND trim(a.result->>'normalizedIssue')<>'' AND a.result->>'primaryCategory'<>'POSITIVE'
        """;}
    private Object[] args(UUID game) {return new Object[]{game,settings.provider(),settings.model(),AnalysisSettings.PROMPT_VERSION};}
    private InputState state(JdbcTemplate db,UUID game) {
        return db.queryForObject("SELECT count(*),md5(coalesce(string_agg(a.id::text || r.created_at_steam::text,',' ORDER BY a.id),'') || ? ) " + eligible(),
            (rs,n)->new InputState(rs.getLong(1),rs.getString(2)),settings.model()+AnalysisSettings.PROMPT_VERSION,game,settings.provider(),settings.model(),AnalysisSettings.PROMPT_VERSION);
    }
    private Snapshot snapshot(UUID game) {
        InputState current=state(jdbc,game);
        return jdbc.query("SELECT * FROM playersignal.issue_snapshot WHERE game_id=?",(rs,n)->new Snapshot(rs.getTimestamp("built_at").toInstant(),rs.getString("algorithm"),rs.getDouble("threshold"),rs.getInt("source_count"),current.count(),!rs.getString("input_fingerprint").equals(current.fingerprint()) || !rs.getString("algorithm").equals(embeddings.algorithm()) || Double.compare(rs.getDouble("threshold"),embeddings.threshold())!=0),game)
            .stream().findFirst().orElse(new Snapshot(null,embeddings.algorithm(),embeddings.threshold(),0,current.count(),true));
    }
    public Page rebuild(UUID game) {
        var selected=games.get(game);
        try(var lock=DatabaseJobLock.acquire(dataSource,Long.MIN_VALUE+selected.steamAppId())) {
            if(lock==null) throw new ApiException(409,"CLUSTERING_BUSY","This game's clustering is already running.");
            lock.connection.setTransactionIsolation(java.sql.Connection.TRANSACTION_REPEATABLE_READ);
            lock.connection.setAutoCommit(false);
            try {
                var state=state(lock.jdbc,game);
                if(state.count()>2000) throw new ApiException(422,"CLUSTER_LIMIT","This alpha supports at most 2000 eligible analyses per game. Existing clusters are preserved.");
                var sources=lock.jdbc.query("SELECT a.id AS analysis_id,r.id AS review_id,r.steam_recommendation_id,a.source_text,a.source_language,r.created_at_steam,a.result " + eligible()+" ORDER BY r.created_at_steam,a.id",(rs,n)->new IssueEngine.Source(rs.getObject("analysis_id",UUID.class),rs.getObject("review_id",UUID.class),rs.getString("steam_recommendation_id"),rs.getString("source_text"),rs.getString("source_language"),rs.getTimestamp("created_at_steam").toInstant(),read(rs.getString("result"),Classification.class)),args(game));
                var vectors=embeddings.vectors(lock.jdbc,sources);
                Instant now=Instant.now();var clusters=new IssueEngine().cluster(game,sources,now,s->vectors.get(s.analysisId()),embeddings.algorithm(),embeddings.threshold());
                lock.jdbc.update("DELETE FROM playersignal.issue_cluster WHERE game_id=?",game);
                for(var c:clusters) {
                    lock.jdbc.update("INSERT INTO playersignal.issue_cluster(id,game_id,title,category,centroid,metrics) VALUES (?,?,?,?,?::jsonb,?::jsonb)",c.id(),game,c.title(),c.category(),json(c.centroid()),json(c.metrics()));
                    for(var m:c.mentions()) lock.jdbc.update("INSERT INTO playersignal.issue_mention(issue_id,analysis_id,similarity) VALUES (?,?,?)",c.id(),m.source().analysisId(),m.similarity());
                }
                lock.jdbc.update("""
                    INSERT INTO playersignal.issue_snapshot(game_id,built_at,input_fingerprint,algorithm,threshold,source_count) VALUES (?,?,?,?,?,?)
                    ON CONFLICT(game_id) DO UPDATE SET built_at=EXCLUDED.built_at,input_fingerprint=EXCLUDED.input_fingerprint,algorithm=EXCLUDED.algorithm,threshold=EXCLUDED.threshold,source_count=EXCLUDED.source_count
                    """,game,java.sql.Timestamp.from(now),state.fingerprint(),embeddings.algorithm(),embeddings.threshold(),sources.size());
                lock.connection.commit();
            } catch(Exception error) {lock.connection.rollback();throw error;}
            finally {lock.connection.setAutoCommit(true);lock.connection.setTransactionIsolation(java.sql.Connection.TRANSACTION_READ_COMMITTED);}
        } catch(ApiException error) {throw error;} catch(Exception error) {throw new ApiException(503,"CLUSTERING_FAILED","Could not rebuild issues. The previous snapshot was preserved. Retry later.");}
        return list(game,0,20);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page list(UUID game,int page,int size) { return list(game,page,size,0); }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page list(UUID game,int page,int size,double minSeverity) {
        return list(game,page,size,minSeverity,null,"severity");
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page list(UUID game,int page,int size,double minSeverity,String category,String sort) {
        games.get(game);validate(page,size);
        if(!Double.isFinite(minSeverity)||minSeverity<0||minSeverity>100)throw new ApiException(400,"INVALID_SEVERITY","Minimum severity must be 0–100.");
        if(category!=null && !category.isBlank()) {
            try { Classification.Category.valueOf(category); }
            catch(IllegalArgumentException e) { throw new ApiException(400,"INVALID_CATEGORY","Unknown issue category."); }
        } else category=null;
        String order=switch(sort) {
            case "severity" -> "(metrics->>'severityScore')::double precision DESC,(metrics->>'mentionCount')::int DESC,id";
            case "mentions" -> "(metrics->>'mentionCount')::int DESC,(metrics->>'severityScore')::double precision DESC,id";
            case "growth" -> "(metrics->>'velocityPercent')::double precision DESC NULLS LAST,(metrics->>'recentMentions')::int DESC,id";
            case "latest" -> "(metrics->>'lastSeenAt')::timestamptz DESC,id";
            default -> throw new ApiException(400,"INVALID_SORT","Sort must be severity, mentions, growth or latest.");
        };
        var snapshot=snapshot(game);
        String where=" WHERE game_id=? AND (metrics->>'severityScore')::double precision>=?";
        var args=new java.util.ArrayList<Object>();args.add(game);args.add(minSeverity);
        if(category!=null){where+=" AND category=?";args.add(category);}
        long total=jdbc.queryForObject("SELECT count(*) FROM playersignal.issue_cluster"+where,Long.class,args.toArray());
        args.add(size);args.add((long)page*size);
        var items=jdbc.query("SELECT * FROM playersignal.issue_cluster"+where+" ORDER BY "+order+" LIMIT ? OFFSET ?",this::summary,args.toArray());
        var states=workflow.states(game);
        return new Page(snapshot,items.stream().map(v->new Summary(v.id(),v.title(),v.category(),states.getOrDefault(IssueWorkflow.key(v.category(),v.title()),"OPEN"),v.metrics())).toList(),page,size,total);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Detail detail(UUID game,UUID issue,int page,int size) {
        games.get(game);validate(page,size);var snapshot=snapshot(game);
        var item=jdbc.query("SELECT * FROM playersignal.issue_cluster WHERE id=? AND game_id=?",this::summary,issue,game).stream().findFirst().orElseThrow(()->new ApiException(404,"ISSUE_NOT_FOUND","Issue not found in this game. Rebuilds may replace issue identifiers."));
        var evidence=jdbc.query("""
            SELECT a.*,r.steam_recommendation_id,r.created_at_steam,m.similarity
            FROM playersignal.issue_mention m JOIN playersignal.review_analysis a ON a.id=m.analysis_id JOIN playersignal.review r ON r.id=a.review_id
            WHERE m.issue_id=? AND r.game_id=? ORDER BY m.similarity DESC,a.id LIMIT ? OFFSET ?
            """,(rs,n)->new Evidence(rs.getObject("id",UUID.class),rs.getObject("review_id",UUID.class),rs.getString("steam_recommendation_id"),rs.getString("source_text"),rs.getString("source_language"),rs.getTimestamp("created_at_steam").toInstant(),read(rs.getString("result"),Classification.class),rs.getDouble("similarity"),rs.getString("response_model"),rs.getString("prompt_version")),issue,game,size,(long)page*size);
        item=new Summary(item.id(),item.title(),item.category(),workflow.get(game,issue).status(),item.metrics());
        return new Detail(snapshot,item,evidence,page,size,item.metrics().mentionCount());
    }
    private Summary summary(java.sql.ResultSet rs,int n)throws java.sql.SQLException {return new Summary(rs.getObject("id",UUID.class),rs.getString("title"),rs.getString("category"),"OPEN",read(rs.getString("metrics"),IssueEngine.Metrics.class));}
    private void validate(int page,int size) {if(page<0 || size<1 || size>100) throw new ApiException(400,"INVALID_PAGE","Page must be non-negative and size between 1 and 100.");}
    private String json(Object value) {try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("Issue serialization failed",e);}}
    private <T>T read(String value,Class<T> type) {try{return mapper.readValue(value,type);}catch(Exception e){throw new IllegalStateException("Invalid stored issue data",e);}}
}
