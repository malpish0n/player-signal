package com.playersignal.usage;
import com.playersignal.game.GameRepository;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
@RestController
public class UsageController {
 private final UsageService usage;private final GameRepository games;
 public UsageController(UsageService usage,GameRepository games){this.usage=usage;this.games=games;}
 @GetMapping("/api/games/{id}/usage") public UsageService.Usage get(@PathVariable UUID id){games.get(id);return usage.status(usage.workspaceForGame(id));}
}
