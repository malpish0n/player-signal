package com.playersignal.issue;
import com.playersignal.analysis.Classification;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static com.playersignal.analysis.Classification.*;
/** Fixed synthetic regression corpus; never persisted in live game tables. */
public final class IssueDemo {
    public record DemoIssue(IssueService.Summary issue,List<IssueService.Evidence> evidence) {}
    public record Result(String mode,Instant asOf,String algorithm,List<DemoIssue> issues) {}
    public static final Instant NOW=Instant.parse("2026-10-01T12:00:00Z");
    public static List<IssueEngine.Source> corpus() {
        var result=new ArrayList<IssueEngine.Source>();
        String[] texts={"The game crashes when joining a lobby.","It crashed when I joined the lobby.","Frame rate drops after update.","The frame rate is dropping after the update.","Save file corruption lost my progress.","My save file corruption lost all progress."};
        String[] titles={"Crash joining lobby","Crashes when joining the lobby","Frame rate drops after update","Frame rate drop after update","Save file corruption","Save file corruption"};
        for(int i=0;i<texts.length;i++) {
            var c=new Classification(Sentiment.NEGATIVE,i==2||i==3?Category.PERFORMANCE:Category.BUG,i>=4?Severity.CRITICAL:Severity.HIGH,.8,titles[i],true,true,List.of(texts[i]),List.of("synthetic"));
            result.add(new IssueEngine.Source(new UUID(0,i+1),new UUID(1,i+1),"synthetic-"+(i+1),texts[i],"english",NOW.minus(i%2==0?2:9,ChronoUnit.DAYS),c));
        }
        return result;
    }
    public static Result create(){return new Result("SYNTHETIC_DEMO",NOW,IssueEngine.VERSION,new IssueEngine().cluster(new UUID(2,1),corpus(),NOW).stream().map(c->new DemoIssue(new IssueService.Summary(c.id(),c.title(),c.category(),"OPEN",c.metrics()),c.mentions().stream().map(m->new IssueService.Evidence(m.source().analysisId(),m.source().reviewId(),m.source().steamId(),m.source().text(),m.source().language(),m.source().seenAt(),m.source().classification(),m.similarity(),"synthetic-fixture","synthetic-v1")).toList())).toList());}
}
