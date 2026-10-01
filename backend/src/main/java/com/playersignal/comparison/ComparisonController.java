package com.playersignal.comparison;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
@RestController
public class ComparisonController {
 private final ComparisonService service;
 public ComparisonController(ComparisonService service){this.service=service;}
 @GetMapping("/api/games/{id}/comparison") public ComparisonService.Comparison get(@PathVariable UUID id,@RequestParam String date,@RequestParam(defaultValue="7")int days){return service.get(id,date,days,Instant.now());}
}
