package com.playersignal.game;

import com.playersignal.shared.ApiException;
import com.playersignal.steam.SteamClient.GameDetails;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class GameRepository {
    public record Game(UUID id, long steamAppId, String name, String headerImageUrl, Instant createdAt) {}
    private final JdbcTemplate jdbc;
    private static final RowMapper<Game> ROW = (rs, row) -> new Game(rs.getObject("id", UUID.class),
            rs.getLong("steam_app_id"), rs.getString("name"), rs.getString("header_image_url"),
            rs.getTimestamp("created_at").toInstant());
    private final com.playersignal.auth.WorkspaceContext workspace;
    public GameRepository(JdbcTemplate jdbc,com.playersignal.auth.WorkspaceContext workspace) { this.jdbc = jdbc; this.workspace=workspace; }
    public List<Game> list() { return jdbc.query("SELECT * FROM playersignal.game WHERE workspace_id=? ORDER BY created_at DESC, id", ROW,workspace.current()); }
    public Game get(UUID id) {
        return jdbc.query("SELECT * FROM playersignal.game WHERE id=?", ROW, id).stream().findFirst()
                .orElseThrow(() -> new ApiException(404, "GAME_NOT_FOUND", "This game is not connected to the workspace."));
    }
    public Game save(GameDetails game) {
        return jdbc.queryForObject("""
                INSERT INTO playersignal.game(id, steam_app_id, name, header_image_url, workspace_id) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (workspace_id,steam_app_id) DO UPDATE SET name=EXCLUDED.name, header_image_url=EXCLUDED.header_image_url
                RETURNING *
                """, ROW, UUID.randomUUID(), game.steamAppId(), game.name(), game.headerImageUrl(),workspace.current());
    }
}
