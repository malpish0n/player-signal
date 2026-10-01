package com.playersignal.analysis;

import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class AnalysisController {
    private final AnalysisService service;
    private final AnalysisRepository repository;
    public AnalysisController(AnalysisService service, AnalysisRepository repository) { this.service = service; this.repository = repository; }
    @GetMapping("/api/games/{id}/analysis") public AnalysisService.Status status(@PathVariable UUID id) { return service.status(id); }
    @PostMapping("/api/games/{id}/analysis") public ResponseEntity<AnalysisRepository.Run> start(@PathVariable UUID id,
            @RequestParam(defaultValue="false") boolean retryFailed) { return ResponseEntity.accepted().body(service.start(id, retryFailed)); }
    @GetMapping("/api/games/{id}/reviews/{reviewId}/analyses") public List<AnalysisRepository.History> history(@PathVariable UUID id, @PathVariable UUID reviewId) {
        repository.requireReview(id, reviewId);
        return repository.history(id, reviewId);
    }
    public record DemoReview(String sourceId, String sourceText, String language, Classification result, String model, String skippedReason) {}
    public record Demo(String mode, String description, List<DemoReview> reviews) {}
    @GetMapping("/api/analysis/demo") public Demo demo() {
        var analyzer = new FixtureReviewAnalyzer();
        var texts = List.of("The game crashes whenever I join a multiplayer lobby.",
                "The frame rate drops after the latest update, even on low settings.",
                "I love the puzzles and the soundtrack.", "gg");
        return new Demo("SYNTHETIC_DEMO", "Deterministic rules on synthetic reviews. Not AI findings and not saved to a game.",
                java.util.stream.IntStream.range(0, texts.size()).mapToObj(i -> {
                    String text = texts.get(i); String skip = AnalysisPolicy.skipReason(text);
                    return new DemoReview("fixture-" + (i + 1), text, "english", skip == null ? analyzer.analyze(new ReviewAnalyzer.Input(text, "english")).classification() : null,
                            "deterministic-fixture-v1", skip);
                }).toList());
    }
}
