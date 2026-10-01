package com.playersignal.analysis;

import java.util.List;
import java.util.Locale;
import static com.playersignal.analysis.Classification.*;

/** Offline demonstration only: never registered as the analyzer for imported reviews. */
public final class FixtureReviewAnalyzer implements ReviewAnalyzer {
    @Override public boolean available() { return true; }
    @Override public Output analyze(Input review) {
        String text = review.text().toLowerCase(Locale.ROOT);
        Category category = text.contains("crash") ? Category.BUG : text.contains("frame rate") ? Category.PERFORMANCE : Category.POSITIVE;
        boolean actionable = category != Category.POSITIVE;
        var classification = new Classification(actionable ? Sentiment.NEGATIVE : Sentiment.POSITIVE, category,
                actionable ? Severity.HIGH : Severity.LOW, 0.55,
                category == Category.BUG ? "Crash when joining a lobby" : category == Category.PERFORMANCE ? "Low frame rate after update" : "",
                actionable, category == Category.BUG, List.of(review.text()), List.of("demo-rule"));
        return new Output(classification.validate(review.text()), "deterministic-fixture-v1", 0, 0);
    }
}
