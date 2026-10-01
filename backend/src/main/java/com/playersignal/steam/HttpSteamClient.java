package com.playersignal.steam;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.playersignal.shared.ApiException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class HttpSteamClient implements SteamClient {
    private final ObjectMapper mapper;
    private final HttpClient http;
    private final String storeBase;
    private final String reviewsBase;

    @org.springframework.beans.factory.annotation.Autowired
    public HttpSteamClient(ObjectMapper mapper) {
        this(mapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                "https://store.steampowered.com", "https://api.steampowered.com");
    }
    // Explicit constructor keeps transport contract tests independent of public Steam.
    public HttpSteamClient(ObjectMapper mapper, HttpClient http, String storeBase, String reviewsBase) {
        this.mapper = mapper;
        this.http = http;
        this.storeBase = storeBase;
        this.reviewsBase = reviewsBase;
    }
    @Override public GameDetails lookup(long appId) {
        JsonNode root = get(storeBase + "/api/appdetails?appids=" + appId + "&l=english&filters=basic");
        JsonNode entry = root.path(Long.toString(appId));
        if (!entry.path("success").isBoolean()) throw malformed();
        if (!entry.path("success").booleanValue())
            throw new ApiException(404, "STEAM_GAME_NOT_FOUND", "Steam could not find this app in the store.");
        JsonNode data = entry.path("data");
        if (data.path("steam_appid").asLong() != appId || !data.path("name").isTextual() || data.path("name").asText().isBlank())
            throw malformed();
        return new GameDetails(appId, data.path("name").asText(), data.path("header_image").asText(null));
    }
    @Override public ReviewPage reviews(long appId, String cursor) {
        try {
            String parameters = mapper.writeValueAsString(Map.of(
                    "appid", appId, "filter", 2, "languages", new String[]{"all"},
                    "purchase_type", 1, "review_type", 0, "num_per_page", 100,
                    "filter_offtopic_activity", false, "cursor", cursor));
            JsonNode response = get(reviewsBase + "/IUserReviewsService/GetAppReviews/v1/?input_json=" +
                    URLEncoder.encode(parameters, StandardCharsets.UTF_8)).path("response");
            if (!response.path("reviews").isArray()) throw malformed();
            var reviews = new ArrayList<SourceReview>();
            for (JsonNode node : response.path("reviews")) {
                if (!node.path("recommendationid").asText().matches("[0-9]+") ||
                        !node.path("review").isTextual() || !node.path("language").isTextual() ||
                        !node.path("voted_up").isBoolean() || !node.path("timestamp_created").canConvertToLong() ||
                        !node.path("timestamp_updated").canConvertToLong()) throw malformed();
                reviews.add(new SourceReview(node.path("recommendationid").asText(), node.path("language").asText(),
                        node.path("review").textValue(), node.path("voted_up").booleanValue(),
                        nonNegative(node.path("votes_up")), nonNegative(node.path("author").path("playtime_forever")),
                        Instant.ofEpochSecond(nonNegative(node.path("timestamp_created"))),
                        Instant.ofEpochSecond(nonNegative(node.path("timestamp_updated"))), node));
            }
            String next = response.path("cursor").asText("");
            if (!reviews.isEmpty() && (next.isBlank() || next.length() > 4096)) throw malformed();
            return new ReviewPage(reviews, next);
        } catch (ApiException error) { throw error; }
        catch (Exception error) { throw malformed(); }
    }
    private long nonNegative(JsonNode node) {
        if (!node.canConvertToLong() || node.longValue() < 0) throw malformed();
        return node.longValue();
    }
    private JsonNode get(String url) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                var request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                        .header("Accept", "application/json").header("User-Agent", "PlayerSignal-local-alpha/0.2").GET().build();
                var response = http.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status == 429 || status >= 500) {
                    if (attempt < 2) { pause(500L << attempt); continue; }
                    throw new ApiException(502, "STEAM_UNAVAILABLE", "Steam is busy or rate-limiting requests. Retry the import later.");
                }
                if (status != 200) throw new ApiException(502, "STEAM_REJECTED", "Steam rejected the request (HTTP " + status + ").");
                return mapper.readTree(response.body());
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new ApiException(503, "IMPORT_INTERRUPTED", "Import interrupted. Retry to resume from the last saved page.");
            } catch (java.io.IOException error) {
                if (attempt == 2) throw new ApiException(502, "STEAM_UNAVAILABLE", "Could not read a valid response from Steam. Retry later.");
                pause(500L << attempt);
            }
        }
        throw malformed();
    }
    private void pause(long millis) {
        try { Thread.sleep(millis); }
        catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ApiException(503, "IMPORT_INTERRUPTED", "Import interrupted. Retry to resume.");
        }
    }
    private ApiException malformed() {
        return new ApiException(502, "STEAM_INVALID_RESPONSE", "Steam returned an unexpected response. No reviews from this page were saved.");
    }
}
