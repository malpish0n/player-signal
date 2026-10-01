package com.playersignal.report;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/games/{game}/reports")
public class ReportController {
 private final ReportService reports;public ReportController(ReportService reports){this.reports=reports;}
 @GetMapping public List<ReportService.Summary> list(@PathVariable UUID game){return reports.list(game);}
 @GetMapping("/{id}") public ReportService.View get(@PathVariable UUID game,@PathVariable UUID id){return reports.get(game,id);}
 @PostMapping("/generate") public ReportService.View generate(@PathVariable UUID game,@RequestBody ReportService.Input input){return reports.create(game,input);}
}
