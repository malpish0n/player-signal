package com.playersignal.steam;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;

public interface SteamClient {
    record GameDetails(long steamAppId, String name, String headerImageUrl) {}
    record SourceReview(String recommendationId, String language, String text,
                        boolean votedUp, long votesUp, long playtimeMinutes,
                        Instant createdAt, Instant updatedAt, JsonNode rawPayload) {}
    record ReviewPage(List<SourceReview> reviews, String cursor) {}
    GameDetails lookup(long appId);
    ReviewPage reviews(long appId, String cursor);
}
