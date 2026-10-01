package com.playersignal.analysis;

import java.util.Locale;
import java.util.Set;

public final class AnalysisPolicy {
    private static final Set<String> UNINFORMATIVE = Set.of("ok", "good", "bad", "nice", "lol", "gg", "me like", "10/10");
    private AnalysisPolicy() {}
    public static String skipReason(String source) {
        String text = source.strip();
        if (text.isEmpty() || text.codePoints().noneMatch(Character::isLetter)) return "No informative written text.";
        if (UNINFORMATIVE.contains(text.toLowerCase(Locale.ROOT))) return "Short generic feedback; no actionable detail.";
        if (source.length() > 12000) return "Review exceeds the 12,000-character analysis limit. Source text is preserved.";
        return null;
    }
}
