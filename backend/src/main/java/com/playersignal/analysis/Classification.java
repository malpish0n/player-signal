package com.playersignal.analysis;

import java.util.List;

public record Classification(Sentiment sentiment, Category primaryCategory, Severity severity,
                             double confidence, String normalizedIssue, boolean isActionable,
                             boolean isLikelyBug, List<String> evidence, List<String> tags) {
    public enum Sentiment { VERY_NEGATIVE, NEGATIVE, MIXED, POSITIVE, VERY_POSITIVE }
    public enum Category { BUG, PERFORMANCE, GAMEPLAY, UI_UX, MULTIPLAYER, BALANCE, CONTENT, CONTROLS, AUDIO, LOCALIZATION, POSITIVE, OTHER }
    public enum Severity { LOW, MEDIUM, HIGH, CRITICAL }
    public Classification validate(String source) {
        if (sentiment == null || primaryCategory == null || severity == null || !Double.isFinite(confidence) ||
                confidence < 0 || confidence > 1 || normalizedIssue == null || normalizedIssue.length() > 240 ||
                evidence == null || evidence.size() > 5 || tags == null || tags.size() > 8 ||
                (isActionable && (normalizedIssue.isBlank() || evidence.isEmpty())) || (isLikelyBug && !isActionable))
            throw AnalysisFailure.invalid();
        for (String quote : evidence)
            if (quote == null || quote.isBlank() || quote.length() > 500 || !source.contains(quote)) throw AnalysisFailure.invalid();
        for (String tag : tags)
            if (tag == null || tag.isBlank() || tag.length() > 40) throw AnalysisFailure.invalid();
        return this;
    }
}
