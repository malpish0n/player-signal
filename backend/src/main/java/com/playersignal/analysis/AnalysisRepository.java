package com.playersignal.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AnalysisRepository {
    public record Source(UUID reviewId, String text, String language, String hash) {}
    public record Run(UUID id, UUID gameId, String provider, String model, String promptVersion, String status,
                      int processed, int succeeded, int skipped, int failed, int cached,
                      Instant startedAt, Instant finishedAt, String error) {}
    public record View(UUID id, String status, String provider, String model, String promptVersion, String responseModel,
                       int attempts, JsonNode result, String error, String skipReason, UUID cachedFrom, Instant updatedAt) {}
    public record History(View analysis, String inputHash, String sourceText, String sourceLanguage) {}
    public record Counts(long total, long pending, long running, long succeeded, long skipped, long failed) {}
    public record Cached(UUID id, Classification result, String responseModel) {}
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final AnalysisSettings settings;

    public AnalysisRepository(JdbcTemplate jdbc, ObjectMapper mapper, AnalysisSettings settings) {
        this.jdbc = jdbc; this.mapper = mapper; this.settings = settings;
    }
    private Object[] identity() { return new Object[]{settings.provider(), settings.model(), AnalysisSettings.PROMPT_VERSION}; }
    private String join() {
        return " LEFT JOIN playersignal.review_analysis a ON a.review_id=r.id AND a.input_hash=r.input_hash AND a.provider=? AND a.model=? AND a.prompt_version=? ";
    }
    /** Correlated filter for the current input/model only; never matches obsolete classifications. */
    public String reviewFilter(String category, String status, UUID issueId, List<Object> args) {
        if (category != null && category.isBlank()) category = null;
        if (status != null && status.isBlank()) status = null;
        if (category != null) try { Classification.Category.valueOf(category); }
        catch (IllegalArgumentException error) { throw new com.playersignal.shared.ApiException(400,"INVALID_CATEGORY","Unknown review category."); }
        if (status != null && !Set.of("PENDING","RUNNING","SUCCEEDED","SKIPPED","FAILED").contains(status))
            throw new com.playersignal.shared.ApiException(400,"INVALID_STATUS","Unknown analysis status.");
        if (category == null && status == null && issueId == null) return "";
        String base = "SELECT 1 FROM playersignal.review_analysis a WHERE a.review_id=r.id AND a.input_hash=r.input_hash AND a.provider=? AND a.model=? AND a.prompt_version=?";
        String sql = "";
        if ("PENDING".equals(status)) {
            sql += " AND NOT EXISTS (" + base + ")";
            args.addAll(Arrays.asList(identity()));
            if (category == null && issueId == null) return sql;
        }
        args.addAll(Arrays.asList(identity()));
        if (status != null && !"PENDING".equals(status)) { base += " AND a.status=?"; args.add(status); }
        if (category != null) { base += " AND a.status='SUCCEEDED' AND a.result->>'primaryCategory'=?"; args.add(category); }
        if (issueId != null) {
            base += " AND a.status='SUCCEEDED' AND EXISTS (SELECT 1 FROM playersignal.issue_mention m JOIN playersignal.issue_cluster c ON c.id=m.issue_id WHERE m.analysis_id=a.id AND c.id=? AND c.game_id=r.game_id)";
            args.add(issueId);
        }
        return sql + " AND EXISTS (" + base + ")";
    }
    public Counts counts(UUID gameId) {
        Object[] identity = identity();
        return jdbc.queryForObject("""
                SELECT count(*) AS total, count(*) FILTER(WHERE a.id IS NULL) AS pending,
                  count(*) FILTER(WHERE a.status='RUNNING') AS running,
                  count(*) FILTER(WHERE a.status='SUCCEEDED') AS succeeded,
                  count(*) FILTER(WHERE a.status='SKIPPED') AS skipped,
                  count(*) FILTER(WHERE a.status='FAILED') AS failed
                FROM playersignal.review_input r
                """ + join() + " WHERE r.game_id=?", (rs, n) -> new Counts(rs.getLong("total"), rs.getLong("pending"),
                rs.getLong("running"), rs.getLong("succeeded"), rs.getLong("skipped"), rs.getLong("failed")),
                identity[0], identity[1], identity[2], gameId);
    }
    public List<Source> candidates(UUID gameId, boolean retryFailed) {
        return jdbc.query("SELECT r.id,r.review_text,r.language,r.input_hash FROM playersignal.review_input r" + join() +
                " WHERE r.game_id=? AND (a.id IS NULL" + (retryFailed ? " OR a.status='FAILED'" : "") +
                ") ORDER BY r.created_at_steam DESC,r.id LIMIT ?", (rs, n) -> new Source(rs.getObject("id", UUID.class),
                rs.getString("review_text"), rs.getString("language"), rs.getString("input_hash")),
                settings.provider(), settings.model(), AnalysisSettings.PROMPT_VERSION, gameId, settings.maxReviews());
    }
    public Run createRun(UUID gameId) {
        return jdbc.queryForObject("INSERT INTO playersignal.analysis_run(id,game_id,provider,model,prompt_version,status) VALUES (?,?,?,?,?,'RUNNING') RETURNING *",
                this::run, UUID.randomUUID(), gameId, settings.provider(), settings.model(), AnalysisSettings.PROMPT_VERSION);
    }
    public Run latest(UUID gameId) {
        return jdbc.query("SELECT * FROM playersignal.analysis_run WHERE game_id=? ORDER BY started_at DESC,id DESC LIMIT 1", this::run, gameId).stream().findFirst().orElse(null);
    }
    public Run getRun(UUID id) { return jdbc.queryForObject("SELECT * FROM playersignal.analysis_run WHERE id=?", this::run, id); }
    public List<UUID> runningGames() {
        return jdbc.query("SELECT game_id FROM playersignal.analysis_run WHERE status='RUNNING' UNION SELECT r.game_id FROM playersignal.review r JOIN playersignal.review_analysis a ON a.review_id=r.id WHERE a.status='RUNNING'", (rs, n) -> rs.getObject(1, UUID.class));
    }
    public void recover(JdbcTemplate connection, UUID gameId) {
        failInflight(connection, "review_id IN (SELECT id FROM playersignal.review WHERE game_id=?)", gameId);
        connection.update("UPDATE playersignal.analysis_run SET status='FAILED',finished_at=now(),error='Analysis interrupted. Retry failed reviews.' WHERE game_id=? AND status='RUNNING'", gameId);
    }
    private void failInflight(JdbcTemplate connection, String predicate, UUID value) {
        connection.update("""
                WITH stopped AS (
                    UPDATE playersignal.review_analysis SET status='FAILED', error='INTERRUPTED: Retry failed reviews.', updated_at=now()
                    WHERE status='RUNNING' AND
                """ + predicate + """
                    RETURNING run_id
                ), totals AS (SELECT run_id,count(*) AS n FROM stopped GROUP BY run_id)
                UPDATE playersignal.analysis_run r SET processed=r.processed+totals.n,failed=r.failed+totals.n
                FROM totals WHERE r.id=totals.run_id
                """, value);
    }
    @org.springframework.transaction.annotation.Transactional
    public void interrupt(UUID runId) {
        failInflight(jdbc, "run_id=?", runId);
        finish(runId, "FAILED", "Analysis interrupted or storage unavailable. Retry failed reviews.");
    }
    public UUID begin(JdbcTemplate connection, Source source, UUID runId) {
        return connection.queryForObject("""
                INSERT INTO playersignal.review_analysis(id, review_id, run_id, input_hash, source_text, source_language, provider, model, prompt_version, status)
                VALUES (?,?,?,?,?,?,?,?,?,'RUNNING')
                ON CONFLICT(review_id,input_hash,provider,model,prompt_version) DO UPDATE
                    SET status='RUNNING',run_id=EXCLUDED.run_id,error=NULL,updated_at=now()
                    WHERE playersignal.review_analysis.status='FAILED'
                RETURNING id
                """, UUID.class, UUID.randomUUID(), source.reviewId(), runId, source.hash(), source.text(), source.language(),
                settings.provider(), settings.model(), AnalysisSettings.PROMPT_VERSION);
    }
    public void attempt(JdbcTemplate connection, UUID id) {
        connection.update("UPDATE playersignal.review_analysis SET attempts=attempts+1,updated_at=now() WHERE id=? AND status='RUNNING'", id);
    }
    public void retryError(JdbcTemplate connection, UUID id, String error) {
        connection.update("UPDATE playersignal.review_analysis SET error=?,updated_at=now() WHERE id=? AND status='RUNNING'", error, id);
    }
    public Cached cached(JdbcTemplate connection, UUID gameId, Source source) {
        return connection.query("""
                SELECT a.* FROM playersignal.review_analysis a JOIN playersignal.review r ON r.id=a.review_id
                WHERE r.game_id=? AND a.input_hash=? AND a.provider=? AND a.model=? AND a.prompt_version=? AND a.status='SUCCEEDED'
                ORDER BY a.created_at,a.id LIMIT 1
                """, (rs, n) -> new Cached(rs.getObject("id", UUID.class), classification(rs.getString("result")), rs.getString("response_model")),
                gameId, source.hash(), settings.provider(), settings.model(), AnalysisSettings.PROMPT_VERSION).stream().findFirst().orElse(null);
    }
    public void complete(JdbcTemplate connection, UUID id, UUID runId, String status, ReviewAnalyzer.Output output,
                         UUID cachedFrom, String skipReason, String error) {
        String result = output == null ? null : json(output.classification());
        connection.update("""
                UPDATE playersignal.review_analysis SET status=?,result=?::jsonb,response_model=?,cached_from=?,skip_reason=?,error=?,
                  input_tokens=?,output_tokens=?,updated_at=now() WHERE id=? AND status='RUNNING'
                """, status, result, output == null ? null : output.responseModel(), cachedFrom, skipReason, error,
                output == null ? 0 : output.inputTokens(), output == null ? 0 : output.outputTokens(), id);
        connection.update("""
                UPDATE playersignal.analysis_run SET processed=processed+1,succeeded=succeeded+?,skipped=skipped+?,failed=failed+?,cached=cached+? WHERE id=?
                """, status.equals("SUCCEEDED") ? 1 : 0, status.equals("SKIPPED") ? 1 : 0, status.equals("FAILED") ? 1 : 0,
                cachedFrom == null ? 0 : 1, runId);
    }
    public void finish(UUID id, String status, String error) {
        jdbc.update("UPDATE playersignal.analysis_run SET status=?,error=?,finished_at=now() WHERE id=? AND status='RUNNING'", status, error, id);
    }
    public Map<UUID, View> current(List<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        var args = new ArrayList<Object>(Arrays.asList(identity())); args.addAll(ids);
        var result = new HashMap<UUID, View>();
        jdbc.query("SELECT a.* FROM playersignal.review_input r" + join() + " WHERE a.id IS NOT NULL AND r.id IN (" +
                String.join(",", Collections.nCopies(ids.size(), "?")) + ")", rs -> {
                    result.put(rs.getObject("review_id", UUID.class), view(rs, 0));
                }, args.toArray());
        return result;
    }
    public List<History> history(UUID gameId, UUID reviewId) {
        return jdbc.query("""
                SELECT a.* FROM playersignal.review_analysis a JOIN playersignal.review r ON r.id=a.review_id
                WHERE r.game_id=? AND r.id=? ORDER BY a.created_at DESC,a.id DESC
                """, (rs, n) -> new History(view(rs, n), rs.getString("input_hash"), rs.getString("source_text"), rs.getString("source_language")), gameId, reviewId);
    }
    public void requireReview(UUID gameId, UUID reviewId) {
        if (jdbc.queryForObject("SELECT count(*) FROM playersignal.review WHERE game_id=? AND id=?", Integer.class, gameId, reviewId) == 0)
            throw new com.playersignal.shared.ApiException(404, "REVIEW_NOT_FOUND", "This review does not belong to the selected game.");
    }
    private Run run(ResultSet rs, int row) throws SQLException {
        return new Run(rs.getObject("id", UUID.class), rs.getObject("game_id", UUID.class), rs.getString("provider"), rs.getString("model"),
                rs.getString("prompt_version"), rs.getString("status"), rs.getInt("processed"), rs.getInt("succeeded"), rs.getInt("skipped"),
                rs.getInt("failed"), rs.getInt("cached"), rs.getTimestamp("started_at").toInstant(),
                rs.getTimestamp("finished_at") == null ? null : rs.getTimestamp("finished_at").toInstant(), rs.getString("error"));
    }
    private View view(ResultSet rs, int row) throws SQLException {
        return new View(rs.getObject("id", UUID.class), rs.getString("status"), rs.getString("provider"), rs.getString("model"),
                rs.getString("prompt_version"), rs.getString("response_model"), rs.getInt("attempts"), tree(rs.getString("result")),
                rs.getString("error"), rs.getString("skip_reason"), rs.getObject("cached_from", UUID.class), rs.getTimestamp("updated_at").toInstant());
    }
    private JsonNode tree(String json) {
        if (json == null) return null;
        try { return mapper.readTree(json); } catch (java.io.IOException error) { throw new IllegalStateException("Stored analysis is invalid", error); }
    }
    private Classification classification(String json) {
        try { return mapper.readValue(json, Classification.class); } catch (java.io.IOException error) { throw new IllegalStateException("Stored analysis is invalid", error); }
    }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); } catch (java.io.IOException error) { throw new IllegalStateException("Analysis serialization failed", error); }
    }
}
