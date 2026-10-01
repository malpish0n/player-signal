package com.playersignal.review;

import com.playersignal.shared.ApiException;
import com.playersignal.analysis.AnalysisRepository;
import com.playersignal.steam.SteamClient.SourceReview;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReviewRepository {
    public record Review(UUID id, String steamRecommendationId, String language, String reviewText,
                         boolean votedUp, long votesUp, long playtimeMinutes, Instant createdAtSteam, Instant updatedAtSteam, AnalysisRepository.View analysis) {}
    public record Page(List<Review> items, long total, int page, int size) {}
    private final JdbcTemplate jdbc;
    private final AnalysisRepository analyses;
    public ReviewRepository(JdbcTemplate jdbc, AnalysisRepository analyses) { this.jdbc = jdbc; this.analyses = analyses; }
    public Page list(UUID gameId, int page, int size, String language, Boolean votedUp) { return list(gameId,page,size,language,votedUp,null); }
    @org.springframework.transaction.annotation.Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Page list(UUID gameId, int page, int size, String language, Boolean votedUp, String query) {
        return list(gameId,page,size,language,votedUp,query,null,null);
    }
    @org.springframework.transaction.annotation.Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Page list(UUID gameId, int page, int size, String language, Boolean votedUp, String query, String from, String to) {
        var range=ReviewDateRange.parse(from,to);
        if(query!=null && query.length()>256) throw new ApiException(400,"INVALID_QUERY","Search text must be at most 256 characters.");
        if (page < 0 || page > 100000 || size < 1 || size > 100 ||
                (language != null && !language.matches("[a-z]{2,32}")))
            throw new ApiException(400, "INVALID_FILTER", "Page must be 0–100000, size 1–100 and language a Steam language code.");
        String where = " WHERE game_id=?";
        var args = new ArrayList<Object>(); args.add(gameId);
        if (language != null) { where += " AND language=?"; args.add(language); }
        if (votedUp != null) { where += " AND voted_up=?"; args.add(votedUp); }
        if (query != null && !query.isBlank()) { where += " AND strpos(lower(review_text), lower(?))>0"; args.add(query.strip()); }
        if (range.start() != null) { where += " AND created_at_steam>=?"; args.add(range.start().atOffset(java.time.ZoneOffset.UTC)); }
        if (range.endExclusive() != null) { where += " AND created_at_steam<?"; args.add(range.endExclusive().atOffset(java.time.ZoneOffset.UTC)); }
        long total = jdbc.queryForObject("SELECT count(*) FROM playersignal.review" + where, Long.class, args.toArray());
        args.add(size); args.add((long) page * size);
        var reviews = jdbc.query("SELECT * FROM playersignal.review" + where + " ORDER BY created_at_steam DESC, id LIMIT ? OFFSET ?",
                (rs, row) -> new Review(rs.getObject("id", UUID.class), rs.getString("steam_recommendation_id"),
                        rs.getString("language"), rs.getString("review_text"), rs.getBoolean("voted_up"), rs.getLong("votes_up"),
                        rs.getLong("playtime_minutes"), rs.getTimestamp("created_at_steam").toInstant(),
                        rs.getTimestamp("updated_at_steam").toInstant(), null), args.toArray());
        var current = analyses.current(reviews.stream().map(Review::id).toList());
        var enriched = reviews.stream().map(r -> new Review(r.id(), r.steamRecommendationId(), r.language(), r.reviewText(), r.votedUp(),
                r.votesUp(), r.playtimeMinutes(), r.createdAtSteam(), r.updatedAtSteam(), current.get(r.id()))).toList();
        return new Page(enriched, total, page, size);
    }
    // Caller supplies the job's locked connection and owns the page transaction.
    public int[] upsert(JdbcTemplate connection, UUID gameId, List<SourceReview> reviews) {
        int inserted = 0, updated = 0;
        for (SourceReview review : reviews) {
            var result = connection.query("""
                INSERT INTO playersignal.review(id, game_id, steam_recommendation_id, language, review_text,
                    voted_up, votes_up, playtime_minutes, created_at_steam, updated_at_steam, raw_payload)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                ON CONFLICT (game_id, steam_recommendation_id) DO UPDATE SET
                    language=EXCLUDED.language, review_text=EXCLUDED.review_text, voted_up=EXCLUDED.voted_up,
                    votes_up=EXCLUDED.votes_up, playtime_minutes=EXCLUDED.playtime_minutes,
                    created_at_steam=EXCLUDED.created_at_steam, updated_at_steam=EXCLUDED.updated_at_steam,
                    raw_payload=EXCLUDED.raw_payload, imported_at=now()
                WHERE playersignal.review.raw_payload IS DISTINCT FROM EXCLUDED.raw_payload
                    AND EXCLUDED.updated_at_steam >= playersignal.review.updated_at_steam
                RETURNING (xmax = 0) AS inserted
                """, (rs, row) -> rs.getBoolean("inserted"), UUID.randomUUID(), gameId, review.recommendationId(),
                    review.language(), review.text(), review.votedUp(), review.votesUp(), review.playtimeMinutes(),
                    Timestamp.from(review.createdAt()), Timestamp.from(review.updatedAt()), review.rawPayload().toString());
            if (!result.isEmpty()) { if (result.getFirst()) inserted++; else updated++; }
        }
        return new int[]{inserted, updated};
    }
}
