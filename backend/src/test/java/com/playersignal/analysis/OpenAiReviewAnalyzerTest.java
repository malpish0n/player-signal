package com.playersignal.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class OpenAiReviewAnalyzerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AnalysisSettings settings = new AnalysisSettings("openai", "test-model", 25, 0);
    private OpenAiReviewAnalyzer client() { return new OpenAiReviewAnalyzer(settings, "test-key", mapper, HttpClient.newHttpClient(), URI.create("http://127.0.0.1:1")); }
    private JsonNode envelope(String type, String content, String status) {
        return mapper.valueToTree(Map.of("status", status, "model", "actual-snapshot", "output", List.of(
                Map.of("type", "reasoning", "summary", List.of()),
                Map.of("type", "message", "content", List.of(Map.of("type", type, "text", content)))), "usage", Map.of("input_tokens", 100, "output_tokens", 20)));
    }
    private String result() throws Exception {
        return mapper.writeValueAsString(new FixtureReviewAnalyzer().analyze(new ReviewAnalyzer.Input("It crashes on join.", "english")).classification());
    }
    @Test void sendsStrictSchemaWithoutAuthorDataAndParsesRawResponsesEnvelope() throws Exception {
        var body = new AtomicReference<JsonNode>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        byte[] response = mapper.writeValueAsBytes(envelope("output_text", result(), "completed"));
        server.createContext("/v1/responses", exchange -> {
            body.set(mapper.readTree(exchange.getRequestBody()));
            exchange.sendResponseHeaders(200, response.length); exchange.getResponseBody().write(response); exchange.close();
        }); server.start();
        try {
            var client = new OpenAiReviewAnalyzer(settings, "test-key", mapper, HttpClient.newHttpClient(), URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/v1/responses"));
            var output = client.analyze(new ReviewAnalyzer.Input("It crashes on join.", "english"));
            assertThat(output.responseModel()).isEqualTo("actual-snapshot");
            assertThat(output.inputTokens()).isEqualTo(100);
            assertThat(body.get().path("store").booleanValue()).isFalse();
            assertThat(body.get().at("/text/format/strict").booleanValue()).isTrue();
            assertThat(body.get().at("/text/format/schema/additionalProperties").booleanValue()).isFalse();
            assertThat(body.get().path("input").get(0).path("content").asText()).contains("untrusted data");
            assertThat(body.get().path("input").get(1).path("content").asText()).doesNotContain("steamid");
        } finally { server.stop(0); }
    }
    @Test void rejectsRefusalIncompleteAndInventedEvidence() throws Exception {
        assertThatThrownBy(() -> client().parse(envelope("refusal", "declined", "completed"), "source"))
                .isInstanceOf(AnalysisFailure.class).hasMessageContaining("declined");
        assertThatThrownBy(() -> client().parse(envelope("output_text", result(), "incomplete"), "It crashes on join.")).isInstanceOf(AnalysisFailure.class);
        assertThatThrownBy(() -> client().parse(envelope("output_text", result(), "completed"), "unrelated text")).isInstanceOf(AnalysisFailure.class);
        String invalid = result().replace("0.55", "1.55");
        assertThatThrownBy(() -> client().parse(envelope("output_text", invalid, "completed"), "It crashes on join.")).isInstanceOf(AnalysisFailure.class);
    }
    @Test void permanentErrorsDoNotLeakProviderBodiesOrKeys() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] data = "sensitive-upstream-debug".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401, data.length); exchange.getResponseBody().write(data); exchange.close();
        }); server.start();
        try {
            var client = new OpenAiReviewAnalyzer(settings, "secret-key", mapper, HttpClient.newHttpClient(), URI.create("http://127.0.0.1:"+server.getAddress().getPort()));
            assertThatThrownBy(() -> client.analyze(new ReviewAnalyzer.Input("source", "english")))
                    .isInstanceOfSatisfying(AnalysisFailure.class, error -> {
                        assertThat(error.retryable).isFalse(); assertThat(error.stopRun).isTrue();
                        assertThat(error.getMessage()).doesNotContain("secret-key", "sensitive-upstream-debug").contains("401");
                    });
        } finally { server.stop(0); }
    }
}
