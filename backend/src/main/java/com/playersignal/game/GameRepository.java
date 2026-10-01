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
    private final com.playersignal.billing.PlanService plans;
    private final JdbcTemplate jdbc;
    private static final RowMapper<Game> ROW = (rs, row) -> new Game(rs.getObject("id", UUID.class),
            rs.getLong("steam_app_id"), rs.getString("name"), rs.getString("header_image_url"),
            rs.getTimestamp("created_at").toInstant());
    private final com.playersignal.auth.WorkspaceContext workspace;
    public GameRepository(JdbcTemplate jdbc,com.playersignal.auth.WorkspaceContext workspace,com.playersignal.billing.PlanService plans) { this.plans=plans;this.jdbc = jdbc; this.workspace=workspace; }
    public List<Game> list() { return jdbc.query("SELECT * FROM playersignal.game WHERE workspace_id=? ORDER BY created_at DESC, id", ROW,workspace.current()); }
    public Game get(UUID id) {
        return jdbc.query("SELECT * FROM playersignal.game WHERE id=?", ROW, id).stream().findFirst()
                .orElseThrow(() -> new ApiException(404, "GAME_NOT_FOUND", "This game is not connected to the workspace."));
    }
    @org.springframework.transaction.annotation.Transactional
    public Game save(GameDetails game) {
        UUID owner=workspace.current();jdbc.queryForObject("SELECT id FROM playersignal.workspace WHERE id=? FOR UPDATE",UUID.class,owner);
        long existing=jdbc.queryForObject("SELECT count(*) FROM playersignal.game WHERE workspace_id=? AND steam_app_id=?",Long.class,owner,game.steamAppId());
        if(existing==0&&jdbc.queryForObject("SELECT count(*) FROM playersignal.game WHERE workspace_id=?",Long.class,owner)>=plans.limits(owner).games())throw new ApiException(422,"GAME_PLAN_LIMIT","This workspace has reached its plan game limit. Existing data is preserved.");
        return jdbc.queryForObject("""
                INSERT INTO playersignal.game(id, steam_app_id, name, header_image_url, workspace_id) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (workspace_id,steam_app_id) DO UPDATE SET name=EXCLUDED.name, header_image_url=EXCLUDED.header_image_url
                RETURNING *
                """, ROW, UUID.randomUUID(), game.steamAppId(), game.name(), game.headerImageUrl(),workspace.current());
    }
}
