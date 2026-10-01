package com.playersignal.issue;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.playersignal.analysis.*;
import com.playersignal.game.GameRepository;
import com.playersignal.steam.SteamClient;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;
@Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class IssueIntegrationTest {
    @Container @ServiceConnection static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine");
    @Autowired JdbcTemplate jdbc;@Autowired IssueService service;@Autowired GameRepository games;@Autowired AnalysisRepository analyses;@Autowired ObjectMapper mapper;@Autowired TestRestTemplate http;
    UUID game;
    @BeforeEach void setup() throws Exception {
        jdbc.update("DELETE FROM playersignal.issue_cluster");jdbc.update("DELETE FROM playersignal.issue_snapshot");jdbc.update("DELETE FROM playersignal.review_analysis");jdbc.update("DELETE FROM playersignal.analysis_run");jdbc.update("DELETE FROM playersignal.review");jdbc.update("DELETE FROM playersignal.game");
        game=games.save(new SteamClient.GameDetails(620,"Synthetic issue test",null)).id();var run=analyses.createRun(game);
        for(var s:IssueDemo.corpus()) {
            jdbc.update("INSERT INTO playersignal.review(id,game_id,steam_recommendation_id,language,review_text,voted_up,votes_up,playtime_minutes,created_at_steam,updated_at_steam,raw_payload) VALUES (?,?,?,'english',?,false,0,60,?,?,'{}'::jsonb)",s.reviewId(),game,s.steamId(),s.text(),java.sql.Timestamp.from(s.seenAt()),java.sql.Timestamp.from(s.seenAt()));
            jdbc.update("INSERT INTO playersignal.review_analysis(id,review_id,run_id,input_hash,source_text,source_language,provider,model,prompt_version,status,result,response_model) SELECT ?,id,?,input_hash,review_text,language,'openai','gpt-4o-mini-2024-07-18',?,'SUCCEEDED',?::jsonb,'test-fixture' FROM playersignal.review_input WHERE id=?",s.analysisId(),run.id(),AnalysisSettings.PROMPT_VERSION,mapper.writeValueAsString(s.classification()),s.reviewId());
        }
        analyses.finish(run.id(),"COMPLETED",null);
    }
    @Test void rebuildIsIdempotentAndEvidenceIsScopedAndPaginated() {
        assertThat(service.list(game,0,20).snapshot().builtAt()).isNull();
        var first=service.rebuild(game);assertThat(first.total()).isEqualTo(3);assertThat(service.list(game,0,20,100).total()).isEqualTo(1);assertThat(first.snapshot().stale()).isFalse();
        var second=service.rebuild(game);assertThat(second.items().stream().map(IssueService.Summary::id)).containsExactlyElementsOf(first.items().stream().map(IssueService.Summary::id).toList());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM playersignal.issue_mention",Integer.class)).isEqualTo(6);
        var detail=service.detail(game,first.items().getFirst().id(),0,1);assertThat(detail.total()).isEqualTo(2);assertThat(detail.evidence()).hasSize(1);
        var next=service.detail(game,detail.issue().id(),1,1);assertThat(next.evidence().getFirst().analysisId()).isNotEqualTo(detail.evidence().getFirst().analysisId());
        var e=detail.evidence().getFirst();assertThat(e.classification().evidence()).allSatisfy(quote->assertThat(e.sourceText()).contains(quote));
        var other=games.save(new SteamClient.GameDetails(621,"Other",null)).id();
        assertThat(http.getForEntity("/api/games/"+other+"/issues/"+detail.issue().id(),String.class).getStatusCode().value()).isEqualTo(404);
        assertThat(http.getForEntity("/api/games/"+game+"/issues?size=0",String.class).getStatusCode().value()).isEqualTo(400);
    }
    @Test void editedSourcesAndOldModelsAreExcludedAfterRebuild() {
        var first=service.rebuild(game);var source=IssueDemo.corpus().getFirst();
        jdbc.update("UPDATE playersignal.review SET review_text='Changed source' WHERE id=?",source.reviewId());
        assertThat(service.list(game,0,20).snapshot().stale()).isTrue();
        service.rebuild(game);assertThat(jdbc.queryForObject("SELECT count(*) FROM playersignal.issue_mention",Integer.class)).isEqualTo(5);
        jdbc.update("UPDATE playersignal.review_analysis SET model='old-model'");
        assertThat(service.list(game,0,20).snapshot().stale()).isTrue();
        var empty=service.rebuild(game);assertThat(empty.items()).isEmpty();assertThat(empty.snapshot().stale()).isFalse();
    }
    @Test void lockedRebuildFailsWithoutChangingSnapshotAndDemoDoesNotWrite() throws Exception {
        var first=service.rebuild(game);
        try(var connection=postgres.createConnection("")) {
            try(var lock=connection.prepareStatement("SELECT pg_advisory_lock(?)")){lock.setLong(1,Long.MIN_VALUE+620);lock.execute();}
            assertThat(http.postForEntity("/api/games/"+game+"/issues/rebuild",null,String.class).getStatusCode().value()).isEqualTo(409);
        }
        assertThat(service.list(game,0,20).snapshot().builtAt()).isEqualTo(first.snapshot().builtAt());
        assertThat(http.getForEntity("/api/issues/demo",String.class).getBody()).contains("SYNTHETIC_DEMO","Save file corruption");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM playersignal.issue_mention",Integer.class)).isEqualTo(6);
    }
    @Test void storageFailureRollsBackWholeReplacement() {
        var first=service.rebuild(game);
        jdbc.execute("CREATE FUNCTION playersignal.reject_issue() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'fixture failure'; END; $$");
        jdbc.execute("CREATE TRIGGER reject_issue BEFORE INSERT ON playersignal.issue_cluster FOR EACH ROW EXECUTE FUNCTION playersignal.reject_issue()");
        try {assertThat(http.postForEntity("/api/games/"+game+"/issues/rebuild",null,String.class).getStatusCode().value()).isEqualTo(503);}
        finally {jdbc.execute("DROP TRIGGER reject_issue ON playersignal.issue_cluster");jdbc.execute("DROP FUNCTION playersignal.reject_issue()");}
        assertThat(service.list(game,0,20).snapshot().builtAt()).isEqualTo(first.snapshot().builtAt());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM playersignal.issue_mention",Integer.class)).isEqualTo(6);
    }
    @Test void filtersAndSortsIssuesWithStablePagesAndMissingGrowthLast() {
        var initial=service.rebuild(game);var ids=initial.items().stream().map(IssueService.Summary::id).toList();
        for(int i=0;i<3;i++) jdbc.update("UPDATE playersignal.issue_cluster SET metrics=metrics || ?::jsonb WHERE id=?",
            "{\"severityScore\":"+(25+i*25)+",\"mentionCount\":"+(i==0?20:10)+",\"velocityPercent\":"+(i==0?"null":i==1?"-25":"100")+",\"lastSeenAt\":\"2026-09-"+(30-i)+"T12:00:00Z\"}",ids.get(i));
        assertThat(service.list(game,0,20,0,null,"severity").items()).extracting(IssueService.Summary::id).containsExactly(ids.get(2),ids.get(1),ids.get(0));
        assertThat(service.list(game,0,20,0,null,"mentions").items()).extracting(IssueService.Summary::id).containsExactly(ids.get(0),ids.get(2),ids.get(1));
        assertThat(service.list(game,0,20,0,null,"growth").items()).extracting(IssueService.Summary::id).containsExactly(ids.get(2),ids.get(1),ids.get(0));
        assertThat(service.list(game,0,20,0,null,"latest").items()).extracting(IssueService.Summary::id).containsExactlyElementsOf(ids);
        var filtered=service.list(game,0,20,50,"BUG","mentions");
        assertThat(filtered.items()).allSatisfy(item->{assertThat(item.category()).isEqualTo("BUG");assertThat(item.metrics().severityScore()).isGreaterThanOrEqualTo(50);});
        assertThat(filtered.total()).isEqualTo(initial.items().subList(1,3).stream().filter(item->item.category().equals("BUG")).count());
        jdbc.update("UPDATE playersignal.issue_cluster SET metrics=metrics || '{\"mentionCount\":10,\"severityScore\":50}'::jsonb WHERE game_id=?",game);
        var full=service.list(game,0,20,0,null,"mentions");
        for(int i=0;i<3;i++) assertThat(service.list(game,i,1,0,null,"mentions").items().getFirst().id()).isEqualTo(full.items().get(i).id());
    }
    @Test void invalidIssueSortAndCategoryAreRejected() {
        assertThat(http.getForEntity("/api/games/"+game+"/issues?sort=unknown",String.class).getStatusCode().value()).isEqualTo(400);
        assertThat(http.getForEntity("/api/games/"+game+"/issues?category=unknown",String.class).getStatusCode().value()).isEqualTo(400);
        assertThat(service.list(game,0,20,0,"AUDIO","latest").items()).isEmpty();
    }

}
