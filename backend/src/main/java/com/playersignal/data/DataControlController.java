package com.playersignal.data;
import com.playersignal.auth.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
@RestController
public class DataControlController {
 private final DataControlService service;private final AuthRateLimit rate;private final WorkspaceContext workspace;
 public DataControlController(DataControlService service,AuthRateLimit rate,WorkspaceContext workspace){this.service=service;this.rate=rate;this.workspace=workspace;}
 @GetMapping("/api/workspace/export") public Map<String,Object> export(){return service.export();}
 private void check(HttpServletRequest req){if(workspace.enabled())rate.check(req.getRemoteAddr(),workspace.principal(workspace.user()).email());}
 @PostMapping("/api/games/{game}/delete") public Map<String,Boolean> deleteGame(@PathVariable UUID game,@RequestBody DataControlService.DeleteInput input,HttpServletRequest req){check(req);service.deleteGame(game,input);return Map.of("deleted",true);}
 @PostMapping("/api/workspace/account/delete") public Map<String,Boolean> deleteAccount(@RequestBody DataControlService.DeleteInput input,HttpServletRequest req){check(req);service.deleteAccount(input);var session=req.getSession(false);if(session!=null)session.invalidate();SecurityContextHolder.clearContext();return Map.of("deleted",true);}
}
