package com.playersignal.steam;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class IngestionRepository {
    public record Run(UUID id, UUID gameId, Instant startedAt, Instant finishedAt, String status,
                      int fetched, int inserted, int updated, String nextCursor, String error) {}
    private final JdbcTemplate jdbc;
    private static final RowMapper<Run> ROW = (rs, row) -> new Run(rs.getObject("id", UUID.class),
            rs.getObject("game_id", UUID.class), rs.getTimestamp("started_at").toInstant(),
            rs.getTimestamp("finished_at") == null ? null : rs.getTimestamp("finished_at").toInstant(),
            rs.getString("status"), rs.getInt("fetched"), rs.getInt("inserted"), rs.getInt("updated"),
            rs.getString("next_cursor"), rs.getString("error"));
    public IngestionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Run latest(UUID gameId) {
        return jdbc.query("SELECT * FROM playersignal.ingestion_run WHERE game_id=? ORDER BY started_at DESC, id DESC LIMIT 1", ROW, gameId)
                .stream().findFirst().orElse(null);
    }
    public List<UUID> runningGames() {
        return jdbc.query("SELECT game_id FROM playersignal.ingestion_run WHERE status='RUNNING'", (rs, row) -> rs.getObject(1, UUID.class));
    }
    public Run create(UUID gameId) {
        Run previous = latest(gameId);
        String cursor = previous != null && !previous.status().equals("COMPLETED") ? previous.nextCursor() : "*";
        return jdbc.queryForObject("INSERT INTO playersignal.ingestion_run(id,game_id,status,next_cursor) VALUES (?,?,'RUNNING',?) RETURNING *",
                ROW, UUID.randomUUID(), gameId, cursor);
    }
    public void failInterrupted(UUID gameId) {
        jdbc.update("UPDATE playersignal.ingestion_run SET status='FAILED', finished_at=now(), error=? WHERE game_id=? AND status='RUNNING'",
                "Import interrupted before completion. Retry to resume from the last saved page.", gameId);
    }
    public void finish(UUID id, String status, String error) {
        jdbc.update("UPDATE playersignal.ingestion_run SET status=?, finished_at=now(), error=? WHERE id=? AND status='RUNNING'", status, error, id);
    }
    public void progress(JdbcTemplate connection, UUID id, int fetched, int inserted, int updated, String cursor) {
        connection.update("""
                UPDATE playersignal.ingestion_run SET fetched=fetched+?, inserted=inserted+?, updated=updated+?, next_cursor=?
                WHERE id=? AND status='RUNNING'
                """, fetched, inserted, updated, cursor, id);
    }
}
