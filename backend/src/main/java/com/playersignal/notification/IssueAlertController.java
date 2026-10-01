package com.playersignal.notification;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/games/{game}/alerts")
public class IssueAlertController {
    public record Input(boolean enabled) {}
    private final IssueAlertService service;
    public IssueAlertController(IssueAlertService service) {this.service=service;}
    @GetMapping public IssueAlertService.Policy get(@PathVariable UUID game) {return service.get(game);}
    @PostMapping public IssueAlertService.Policy save(@PathVariable UUID game,@RequestBody Input input) {return service.save(game,input.enabled());}
    @PostMapping("/check") public IssueAlertService.Result check(@PathVariable UUID game) {return service.check(game,true);}
}
