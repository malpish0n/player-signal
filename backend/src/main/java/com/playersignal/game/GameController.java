package com.playersignal.game;

import com.playersignal.review.ReviewRepository;
import com.playersignal.steam.IngestionRepository;
import com.playersignal.steam.IngestionService;
import com.playersignal.steam.SteamAppId;
import com.playersignal.steam.SteamClient;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/games")
public class GameController {
    public record AddGame(String steamApp) {}
    private final com.playersignal.usage.UsageService usage;
    private final com.playersignal.auth.WorkspaceContext workspace;
    private final GameRepository games;
    private final SteamClient steam;
    private final ReviewRepository reviews;
    private final IngestionService ingestion;
    private final IngestionRepository runs;
    public GameController(GameRepository games, SteamClient steam, ReviewRepository reviews,
                          IngestionService ingestion, IngestionRepository runs, com.playersignal.usage.UsageService usage, com.playersignal.auth.WorkspaceContext workspace) {
        this.usage=usage;this.workspace=workspace;
        this.games = games; this.steam = steam; this.reviews = reviews; this.ingestion = ingestion; this.runs = runs;
    }
    @PostMapping("/preview") public SteamClient.GameDetails preview(@RequestBody AddGame body) {
        long app=SteamAppId.parse(body.steamApp());var event=usage.reserve(workspace.current(),null,null,"GAME_LOOKUP",null);
        try {var details=steam.lookup(app);usage.finish(event,true,0,0);return details;}catch(RuntimeException error){usage.finish(event,false,0,0);throw error;}
    }
    @GetMapping public List<GameRepository.Game> list() { return games.list(); }
    @PostMapping public GameRepository.Game add(@RequestBody AddGame body) {
        long app=SteamAppId.parse(body.steamApp());
        var event=usage.reserve(workspace.current(),null,null,"GAME_LOOKUP",null);
        try {var saved=games.save(steam.lookup(app));usage.finish(event,true,0,0);return saved;}
        catch(RuntimeException error){usage.finish(event,false,0,0);throw error;}
    }
    @GetMapping("/{id}") public GameRepository.Game get(@PathVariable UUID id) { return games.get(id); }
    @PostMapping("/{id}/sync") public ResponseEntity<IngestionRepository.Run> sync(@PathVariable UUID id) {
        return ResponseEntity.accepted().body(ingestion.start(id));
    }
    @GetMapping("/{id}/sync/latest") public ResponseEntity<IngestionRepository.Run> latest(@PathVariable UUID id) {
        games.get(id);
        var latest = runs.latest(id);
        return latest == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(latest);
    }
    @GetMapping("/{id}/reviews") public ReviewRepository.Page reviews(@PathVariable UUID id,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size,
            @RequestParam(required=false) String language, @RequestParam(required=false) Boolean votedUp, @RequestParam(required=false) String q, @RequestParam(required=false) String from, @RequestParam(required=false) String to, @RequestParam(required=false) String category, @RequestParam(required=false) String status, @RequestParam(required=false) UUID issueId) {
        games.get(id);
        return reviews.list(id, page, size, language, votedUp, q, from, to, category, status, issueId);
    }
}
