package com.playersignal.notification;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/games/{game}")
public class AutomationController {
 private final AutomationService service;public AutomationController(AutomationService service){this.service=service;}
 @GetMapping("/automation") public AutomationService.Policy get(@PathVariable UUID game){return service.get(game);}
 @PostMapping("/automation") public AutomationService.Policy save(@PathVariable UUID game,@RequestBody AutomationService.Input input){return service.save(game,input);}
 @GetMapping("/notifications") public List<Map<String,Object>> notifications(@PathVariable UUID game){return service.notifications(game);}
 @PostMapping("/notifications/read") public Map<String,Boolean> read(@PathVariable UUID game){service.read(game);return Map.of("read",true);}
}
