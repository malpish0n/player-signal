package com.playersignal.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

public class OpenAiReviewAnalyzer implements ReviewAnalyzer {
    private final AnalysisSettings settings;
    private final String key;
    private final ObjectMapper mapper;
    private final HttpClient http;
    private final URI endpoint;
    private final JsonNode schema;
    private final String prompt;

    @Autowired
    public OpenAiReviewAnalyzer(AnalysisSettings settings, @Value("${OPENAI_API_KEY:}") String key, ObjectMapper mapper) {
        this(settings, key, mapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), URI.create("https://api.openai.com/v1/responses"));
    }
    public OpenAiReviewAnalyzer(AnalysisSettings settings, String key, ObjectMapper mapper, HttpClient http, URI endpoint) {
        this.settings = settings; this.key = key; this.mapper = mapper; this.http = http; this.endpoint = endpoint;
        try (var schemaInput = new ClassPathResource("analysis/classification-schema.json").getInputStream();
             var promptInput = new ClassPathResource("analysis/prompt-v2.txt").getInputStream()) {
            schema = mapper.readTree(schemaInput);
            prompt = new String(promptInput.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException error) { throw new IllegalStateException("Analysis contract resources missing", error); }
    }
    @Override public boolean available() { return settings.mode().equals("openai") && !key.isBlank(); }
    @Override public Output analyze(Input review) {
        if (!available()) throw new AnalysisFailure("NOT_CONFIGURED", "Set ANALYSIS_PROVIDER=openai and OPENAI_API_KEY on the backend.", false, true);
        try {
            var body = Map.of("model", settings.model(), "store", false, "max_output_tokens", 1200,
                    "input", List.of(Map.of("role", "system", "content", prompt),
                            Map.of("role", "user", "content", mapper.writeValueAsString(Map.of("language", review.language(), "reviewText", review.text())))),
                    "text", Map.of("format", Map.of("type", "json_schema", "name", "review_classification", "strict", true, "schema", schema)));
            var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(45))
                    .header("Authorization", "Bearer " + key).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 429 || status >= 500)
                throw new AnalysisFailure("PROVIDER_BUSY", "OpenAI is unavailable or rate-limiting requests. Retry failed reviews later.", true, true);
            if (status != 200)
                throw new AnalysisFailure("PROVIDER_REJECTED", "OpenAI rejected the request (HTTP " + status + "). Check credentials, model access and configuration.", false, true);
            return parse(mapper.readTree(response.body()), review.text());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AnalysisFailure("INTERRUPTED", "Analysis interrupted. Retry failed reviews to resume.", false, true);
        } catch (java.io.IOException error) {
            throw new AnalysisFailure("PROVIDER_CONNECTION", "Could not read an OpenAI response. Retry failed reviews later.", true, true);
        }
    }
    Output parse(JsonNode response, String source) {
        if (response == null || !response.path("status").asText().equals("completed")) throw AnalysisFailure.invalid();
        String text = null;
        for (JsonNode output : response.path("output")) {
            if (!output.path("type").asText().equals("message")) continue;
            for (JsonNode content : output.path("content")) {
                if (content.path("type").asText().equals("refusal"))
                    throw new AnalysisFailure("REFUSED", "The provider declined to classify this review.", false, false);
                if (content.path("type").asText().equals("output_text")) {
                    if (text != null || !content.path("text").isTextual()) throw AnalysisFailure.invalid();
                    text = content.path("text").textValue();
                }
            }
        }
        if (text == null) throw AnalysisFailure.invalid();
        try {
            JsonNode value = mapper.readTree(text);
            // Validate types before Jackson binding so strings/numbers cannot coerce to booleans.
            if (value == null || !value.isObject() || value.size() != 9) throw AnalysisFailure.invalid();
            for (String field : List.of("sentiment", "primaryCategory", "severity", "normalizedIssue"))
                if (!value.path(field).isTextual()) throw AnalysisFailure.invalid();
            if (!value.path("confidence").isNumber() || !value.path("isActionable").isBoolean() ||
                    !value.path("isLikelyBug").isBoolean() || !value.path("evidence").isArray() || !value.path("tags").isArray()) throw AnalysisFailure.invalid();
            for (String field : List.of("evidence", "tags"))
                for (JsonNode item : value.path(field)) if (!item.isTextual()) throw AnalysisFailure.invalid();
            Classification result = mapper.treeToValue(value, Classification.class).validate(source);
            String actualModel = response.path("model").asText();
            if (actualModel.isBlank()) throw AnalysisFailure.invalid();
            return new Output(result, actualModel, Math.max(0, response.path("usage").path("input_tokens").asLong()),
                    Math.max(0, response.path("usage").path("output_tokens").asLong()));
        } catch (java.io.IOException | IllegalArgumentException error) { throw AnalysisFailure.invalid(); }
    }
}
