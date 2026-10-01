package com.playersignal.analysis;

public interface ReviewAnalyzer {
    record Input(String text, String language) {}
    record Output(Classification classification, String responseModel, long inputTokens, long outputTokens) {}
    boolean available();
    Output analyze(Input review);
}
