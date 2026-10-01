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
        if (!mode.equals("disabled") && !mode.equals("openai")) throw new IllegalArgumentException("Analysis provider must be disabled or openai");
        if (model.isBlank() || maxReviews < 1 || maxReviews > 100 || retryDelayMs < 0 || retryDelayMs > 30000)
            throw new IllegalArgumentException("Invalid analysis limits or model");
        this.mode = mode; this.model = model; this.maxReviews = maxReviews; this.retryDelayMs = retryDelayMs;
    }
}
