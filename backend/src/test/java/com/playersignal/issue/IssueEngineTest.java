package com.playersignal.issue;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import com.playersignal.analysis.Classification;
class IssueEngineTest {
    @Test void corpusProducesThreeStableClustersRegardlessOfInputOrder() {
        var input=new ArrayList<>(IssueDemo.corpus());var engine=new IssueEngine();var game=UUID.randomUUID();
        var first=engine.cluster(game,input,IssueDemo.NOW);Collections.reverse(input);var second=engine.cluster(game,input,IssueDemo.NOW);
        assertThat(first).hasSize(3);assertThat(first.stream().map(IssueEngine.Cluster::id)).containsExactlyElementsOf(second.stream().map(IssueEngine.Cluster::id).toList());
        assertThat(first).allSatisfy(c->{assertThat(c.metrics().mentionCount()).isEqualTo(2);assertThat(c.metrics().recentMentions()).isEqualTo(1);assertThat(c.metrics().previousMentions()).isEqualTo(1);assertThat(c.metrics().velocityPercent()).isZero();assertThat(c.metrics().negativeRatio()).isEqualTo(1);});
        assertThat(first.getFirst().metrics().severityScore()).isEqualTo(100);
    }
    @Test void normalizesPunctuationInflectionsAndUnicode() {
        assertThat(IssueEngine.cosine(IssueEngine.vector("CRASHES when joining the lobby!"),IssueEngine.vector("Crash join lobby"))).isCloseTo(1,within(1e-10));
        assertThat(IssueEngine.normalize("ＦＲＡＭＥ—rate")).isEqualTo("frame rate");
    }
    @Test void rejectsCrossCategoryAndNonActionableMatches() {
        var source=IssueDemo.corpus().getFirst();var c=source.classification();
        var changed=new Classification(c.sentiment(),Classification.Category.AUDIO,c.severity(),c.confidence(),c.normalizedIssue(),true,false,c.evidence(),c.tags());
        var other=new IssueEngine.Source(UUID.randomUUID(),UUID.randomUUID(),"other",source.text(),source.language(),source.seenAt(),changed);
        assertThat(new IssueEngine().cluster(UUID.randomUUID(),List.of(source,other),IssueDemo.NOW)).hasSize(2);
        var nonActionable=new Classification(c.sentiment(),c.primaryCategory(),c.severity(),c.confidence(),"",false,false,c.evidence(),c.tags());
        assertThat(new IssueEngine().cluster(UUID.randomUUID(),List.of(new IssueEngine.Source(source.analysisId(),source.reviewId(),source.steamId(),source.text(),source.language(),source.seenAt(),nonActionable)),IssueDemo.NOW)).isEmpty();
    }
    @Test void zeroBaselineHasNoInventedGrowthPercentage() {
        var metrics=IssueEngine.metrics(List.of(IssueDemo.corpus().getFirst()),IssueDemo.NOW);
        assertThat(metrics.velocityPercent()).isNull();assertThat(metrics.recentMentions()).isEqualTo(1);
    }
}
