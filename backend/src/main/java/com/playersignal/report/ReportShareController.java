package com.playersignal.report;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
public class ReportShareController {
    private final ReportShareService shares;
    public ReportShareController(ReportShareService shares){this.shares=shares;}
    @GetMapping("/api/games/{game}/reports/{report}/share") public ReportShareService.State state(@PathVariable UUID game,@PathVariable UUID report){return shares.state(game,report);}
    @PostMapping("/api/games/{game}/reports/{report}/share") public ReportShareService.Created create(@PathVariable UUID game,@PathVariable UUID report,@RequestBody ReportShareService.Input input){return shares.create(game,report,input);}
    @PostMapping("/api/games/{game}/reports/{report}/share/revoke") public ReportShareService.State revoke(@PathVariable UUID game,@PathVariable UUID report){return shares.revoke(game,report);}
    @GetMapping("/api/shared-reports/{token}") public ResponseEntity<?> read(@PathVariable String token){return ResponseEntity.ok().header("Cache-Control","no-store").header("Referrer-Policy","no-referrer").header("X-Robots-Tag","noindex, nofollow, noarchive").body(shares.publicReport(token));}
}
