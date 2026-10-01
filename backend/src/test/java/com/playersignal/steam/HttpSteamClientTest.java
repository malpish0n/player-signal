package com.playersignal.steam;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.playersignal.shared.ApiException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class HttpSteamClientTest {
    @Test void mapsVerifiedServiceEnvelopeAndEncodesCursor() throws Exception {
        var mapper = new ObjectMapper();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var query = new java.util.concurrent.atomic.AtomicReference<String>();
        server.createContext("/IUserReviewsService/GetAppReviews/v1/", exchange -> {
            query.set(exchange.getRequestURI().getRawQuery());
            byte[] body = """
                {"response":{"reviews":[{"recommendationid":"42","language":"english","review":" exact\\ntext <b> ",
                "voted_up":false,"votes_up":2,"timestamp_created":1700000000,"timestamp_updated":1700000001,
                "author":{"playtime_forever":120}}],"cursor":"next+/="}}
                """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            var page = new HttpSteamClient(mapper, HttpClient.newHttpClient(), base, base).reviews(620, "a+/=");
            assertThat(page.reviews().getFirst().text()).isEqualTo(" exact\ntext <b> ");
            assertThat(page.reviews().getFirst().playtimeMinutes()).isEqualTo(120);
            var parameters = mapper.readTree(URLDecoder.decode(query.get().substring("input_json=".length()), StandardCharsets.UTF_8));
            assertThat(parameters.path("cursor").asText()).isEqualTo("a+/=");
            assertThat(parameters.path("filter").asInt()).isEqualTo(2);
            assertThat(parameters.path("purchase_type").asInt()).isEqualTo(1);
        } finally { server.stop(0); }
    }
    @Test void retriesTransientStatusButRejectsMalformedPayload() throws Exception {
        var count = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            int status = count.incrementAndGet() == 1 ? 429 : 200;
            byte[] body = "{\"response\":{}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            var client = new HttpSteamClient(new ObjectMapper(), HttpClient.newHttpClient(), base, base);
            assertThatThrownBy(() -> client.reviews(620, "*")).isInstanceOf(ApiException.class).hasMessageContaining("unexpected response");
            assertThat(count).hasValue(2);
        } finally { server.stop(0); }
    }
}
