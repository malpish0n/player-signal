package com.playersignal.preference;
import com.playersignal.shared.ApiException;
import java.net.URI;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/games/{game}/saved-views")
public class SavedViewController {
 public record Input(String name,String path) {} public record View(UUID id,String name,String path) {}
 private final JdbcTemplate jdbc;public SavedViewController(JdbcTemplate jdbc){this.jdbc=jdbc;}
 @GetMapping public List<View> list(@PathVariable UUID game){return jdbc.query("SELECT id,name,path FROM playersignal.saved_view WHERE game_id=? ORDER BY created_at DESC,id",(rs,n)->new View(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3)),game);}
 @PostMapping @Transactional public View save(@PathVariable UUID game,@RequestBody Input input){
  if(input.name()==null||input.name().isBlank()||input.name().length()>80||input.path()==null||input.path().length()>2048)throw new ApiException(400,"INVALID_VIEW","Provide a short name and a valid view.");
  try{URI uri=URI.create(input.path());if(uri.isAbsolute()||uri.getRawAuthority()!=null||uri.getFragment()!=null||!Set.of("/games/"+game,"/games/"+game+"/issues").contains(uri.getRawPath()))throw new IllegalArgumentException();}catch(IllegalArgumentException e){throw new ApiException(400,"INVALID_VIEW","Save only a review or issue view from this game.");}
  jdbc.queryForObject("SELECT id FROM playersignal.game WHERE id=? FOR UPDATE",UUID.class,game);if(jdbc.queryForObject("SELECT count(*) FROM playersignal.saved_view WHERE game_id=?",Long.class,game)>=20)throw new ApiException(422,"VIEW_LIMIT","Remove a saved view before adding another (limit 20).");
  UUID id=UUID.randomUUID();jdbc.update("INSERT INTO playersignal.saved_view(id,game_id,name,path) VALUES (?,?,?,?)",id,game,input.name().strip(),input.path());return new View(id,input.name().strip(),input.path());
 }
 @PostMapping("/{id}/remove") public Map<String,Boolean> remove(@PathVariable UUID game,@PathVariable UUID id){jdbc.update("DELETE FROM playersignal.saved_view WHERE id=? AND game_id=?",id,game);return Map.of("removed",true);}
}
