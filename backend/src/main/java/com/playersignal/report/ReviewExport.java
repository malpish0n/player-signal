package com.playersignal.report;

import com.playersignal.game.GameRepository;
import com.playersignal.review.ReviewRepository;
import com.playersignal.shared.ApiException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewExport {
    public static final int MAX_ROWS = 5000;
    static final int MAX_BYTES = 10 * 1024 * 1024;
    private final GameRepository games;
    private final ReviewRepository reviews;
    public ReviewExport(GameRepository games, ReviewRepository reviews) { this.games = games; this.reviews = reviews; }

    // One database snapshot across all pages; reject limits before sending any download bytes.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public byte[] csv(UUID game, String language, Boolean votedUp, String q, String from, String to) {
        games.get(game);
        var first = reviews.list(game, 0, 100, language, votedUp, q, from, to);
        if (first.total() > MAX_ROWS) throw new ApiException(422, "EXPORT_TOO_LARGE", "Export supports up to 5000 matching reviews. Narrow your filters and retry.");
        var output = new ByteArrayOutputStream();
        append(output, "\uFEFF");
        append(output, row("review_id", "steam_review_id", "language", "recommended", "helpful_votes", "playtime_minutes", "created_at_utc", "updated_at_utc", "review_text"));
        var page = first;
        for (int index = 0; ; index++) {
            for (var r : page.items()) append(output, row(r.id().toString(), r.steamRecommendationId(), r.language(),
                Boolean.toString(r.votedUp()), Long.toString(r.votesUp()), Long.toString(r.playtimeMinutes()),
                r.createdAtSteam().toString(), r.updatedAtSteam().toString(), r.reviewText()));
            if ((long)(index + 1) * 100 >= first.total()) break;
            page = reviews.list(game, index + 1, 100, language, votedUp, q, from, to);
        }
        return output.toByteArray();
    }
    static String row(String... values) {
        var result = new StringBuilder();
        for (String value : values) {
            if (value.length() > MAX_BYTES) throw tooLarge();
            if (!result.isEmpty()) result.append(',');
            String trimmed = value.stripLeading();
            // Quote escaping alone does not prevent spreadsheet formula execution.
            if ((!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0) ||
                    value.startsWith("\t") || value.startsWith("\r") || value.startsWith("\n")) value = "'" + value;
            result.append('"').append(value.replace("\"", "\"\"")).append('"');
        }
        return result.append("\r\n").toString();
    }
    static void append(ByteArrayOutputStream output, String text) {
        var bytes = text.getBytes(StandardCharsets.UTF_8);
        if ((long)output.size() + bytes.length > MAX_BYTES) throw tooLarge();
        output.writeBytes(bytes);
    }
    private static ApiException tooLarge() { return new ApiException(422, "EXPORT_TOO_LARGE", "Export exceeds 10 MiB. Narrow your filters and retry."); }
}
