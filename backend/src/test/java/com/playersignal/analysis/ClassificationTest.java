package com.playersignal.analysis;

import java.util.List;
import org.junit.jupiter.api.Test;
import static com.playersignal.analysis.Classification.*;
import static org.assertj.core.api.Assertions.*;

class ClassificationTest {
    @Test void evidenceMustBeExactSourceSubstring() {
        var result = new Classification(Sentiment.NEGATIVE, Category.BUG, Severity.HIGH, .8, "Lobby crash", true, true, List.of("crashes on join"), List.of("lobby"));
        assertThat(result.validate("The game crashes on join every time.")).isSameAs(result);
        assertThatThrownBy(() -> result.validate("The game is great.")).isInstanceOf(AnalysisFailure.class);
    }
    @Test void policyPreservesShortButActionableSymptoms() {
        assertThat(AnalysisPolicy.skipReason("crash")).isNull();
        assertThat(AnalysisPolicy.skipReason("lag")).isNull();
        assertThat(AnalysisPolicy.skipReason("GG")).isNotNull();
        assertThat(AnalysisPolicy.skipReason("   ")).isNotNull();
        assertThat(AnalysisPolicy.skipReason("x".repeat(12001))).contains("limit");
    }
    @Test void fakeAnalyzerIsDeterministicAndUsesSourceEvidence() {
        var input = new ReviewAnalyzer.Input("It crashes when I join a lobby.", "english");
        var analyzer = new FixtureReviewAnalyzer();
        assertThat(analyzer.analyze(input)).isEqualTo(analyzer.analyze(input));
        assertThat(analyzer.analyze(input).classification().primaryCategory()).isEqualTo(Category.BUG);
        assertThat(analyzer.analyze(input).classification().evidence()).containsExactly(input.text());
    }
}
