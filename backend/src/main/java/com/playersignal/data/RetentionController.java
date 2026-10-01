package com.playersignal.data;
import com.playersignal.auth.WorkspaceContext;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/games/{game}/retention")
public class RetentionController {
    private final RetentionService service;private final WorkspaceContext workspace;
    public RetentionController(RetentionService service,WorkspaceContext workspace){this.service=service;this.workspace=workspace;}
    @GetMapping public RetentionService.Policy get(@PathVariable UUID game){workspace.requireOwner();return service.get(game);}
    @PostMapping public RetentionService.Policy save(@PathVariable UUID game,@RequestBody RetentionService.Input input){workspace.requireOwner();return service.save(game,input);}
    @PostMapping("/preview") public RetentionService.Counts preview(@PathVariable UUID game,@RequestBody RetentionService.Input input){workspace.requireOwner();return service.preview(game,input);}
}
