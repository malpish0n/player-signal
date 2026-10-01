package com.playersignal.overview;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
@RestController
public class OverviewController {
    private final OverviewService service;
    public OverviewController(OverviewService service){this.service=service;}
    @GetMapping("/api/games/{id}/overview") public OverviewService.Overview get(@PathVariable UUID id,@RequestParam(defaultValue="30")int days){return service.get(id,days,Instant.now());}
    @GetMapping("/api/overview/demo") public OverviewService.Overview demo(@RequestParam(defaultValue="30")int days){return OverviewService.demo(days);}
}
