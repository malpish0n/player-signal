package com.playersignal.report;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/games/{id}/reviews/export")
public class ReviewExportController {
    private final ReviewExport export;
    public ReviewExportController(ReviewExport export) { this.export = export; }
    @GetMapping
    public ResponseEntity<byte[]> csv(@PathVariable UUID id, @RequestParam(required=false) String language,
            @RequestParam(required=false) Boolean votedUp, @RequestParam(required=false) String q, @RequestParam(required=false) String from, @RequestParam(required=false) String to, @RequestParam(required=false) String category, @RequestParam(required=false) String status, @RequestParam(required=false) UUID issueId) {
        byte[] content = export.csv(id, language, votedUp, q, from, to, category, status, issueId);
        return ResponseEntity.ok().header("Content-Type", "text/csv; charset=UTF-8")
            .header("Content-Disposition", "attachment; filename=\"playersignal-" + id + "-reviews.csv\"")
            .header("Cache-Control", "no-store").header("X-Content-Type-Options", "nosniff").body(content);
    }
}
