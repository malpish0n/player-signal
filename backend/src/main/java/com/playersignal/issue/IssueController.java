package com.playersignal.issue;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
@RestController
public class IssueController {
    private final IssueService service;
    public IssueController(IssueService service){this.service=service;}
    @GetMapping("/api/games/{game}/issues") public IssueService.Page list(@PathVariable UUID game,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,@RequestParam(defaultValue="0")double minSeverity,@RequestParam(required=false)String category,@RequestParam(defaultValue="severity")String sort){return service.list(game,page,size,minSeverity,category,sort);}
    @PostMapping("/api/games/{game}/issues/rebuild") public IssueService.Page rebuild(@PathVariable UUID game){return service.rebuild(game);}
    @GetMapping("/api/games/{game}/issues/{issue}") public IssueService.Detail detail(@PathVariable UUID game,@PathVariable UUID issue,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return service.detail(game,issue,page,size);}
    @GetMapping("/api/issues/demo") public IssueDemo.Result demo(){return IssueDemo.create();}
}
