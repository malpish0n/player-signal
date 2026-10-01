package com.playersignal.analysis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record AnalysisSettings(String mode, String model, int maxReviews, int retryDelayMs) {
    public static final String PROMPT_VERSION = "review-classification-v1";
    public static final String PROVIDER = "openai";
    public AnalysisSettings(@Value("${playersignal.analysis.provider:disabled}") String mode,
                            @Value("${playersignal.analysis.model:gpt-4o-mini-2024-07-18}") String model,
                            @Value("${playersignal.analysis.max-reviews:25}") int maxReviews,
                            @Value("${playersignal.analysis.retry-delay-ms:500}") int retryDelayMs) {
        if (!java.util.Set.of("disabled","openai","local","ollama").contains(mode)) throw new IllegalArgumentException("Unknown analysis provider");
        if (model.isBlank() || maxReviews < 1 || maxReviews > 100 || retryDelayMs < 0 || retryDelayMs > 30000)
            throw new IllegalArgumentException("Invalid analysis limits or model");
        if(mode.equals("ollama") && (model.isBlank()||model.toLowerCase(java.util.Locale.ROOT).contains("cloud")))throw new IllegalArgumentException("Choose an installed local model, not a cloud model");
        this.mode = mode; this.model = mode.equals("local")?LocalReviewAnalyzer.MODEL:model; this.maxReviews = maxReviews; this.retryDelayMs = retryDelayMs;
    }
    public String provider(){return mode.equals("local")?"local-rules":mode.equals("ollama")?"ollama":"openai";}
}
