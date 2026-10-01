package com.playersignal.update;

import com.playersignal.game.GameRepository;
import com.playersignal.review.ReviewDateRange;
import com.playersignal.shared.ApiException;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/games/{game}/updates")
public class UpdateController {
 public record Update(UUID id,String title,LocalDate releasedOn,int version,Instant createdAt,Instant updatedAt) {}
 public record Input(String title,String releasedOn,Integer version) {}
 private final JdbcTemplate jdbc;private final GameRepository games;
 private static final RowMapper<Update> ROW=(rs,n)->new Update(rs.getObject("id",UUID.class),rs.getString("title"),rs.getObject("released_on",LocalDate.class),rs.getInt("version"),rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("updated_at").toInstant());
 public UpdateController(JdbcTemplate jdbc,GameRepository games){this.jdbc=jdbc;this.games=games;}
 @GetMapping public List<Update> list(@PathVariable UUID game){games.get(game);return jdbc.query("SELECT * FROM playersignal.game_update WHERE game_id=? ORDER BY released_on DESC,id",ROW,game);}
 private LocalDate validate(Input input){
  if(input.title()==null||input.title().strip().isEmpty()||input.title().length()>120||input.title().chars().anyMatch(Character::isISOControl))throw new ApiException(400,"INVALID_UPDATE","Enter a title of 1–120 characters without line breaks.");
  var dates=ReviewDateRange.parse(input.releasedOn(),null);
  if(dates.start()==null)throw new ApiException(400,"INVALID_UPDATE","Choose a release date (UTC).");
  return dates.start().atZone(ZoneOffset.UTC).toLocalDate();
 }
 @PostMapping @Transactional public Update create(@PathVariable UUID game,@RequestBody Input input){
  games.get(game);var date=validate(input);
  // Serialize the limit check for concurrent writers to the same game.
  jdbc.queryForObject("SELECT id FROM playersignal.game WHERE id=? FOR UPDATE",UUID.class,game);
  if(jdbc.queryForObject("SELECT count(*) FROM playersignal.game_update WHERE game_id=?",Long.class,game)>=100)throw new ApiException(422,"UPDATE_LIMIT","This game already has 100 saved updates.");
  return jdbc.queryForObject("INSERT INTO playersignal.game_update(id,game_id,title,released_on) VALUES (?,?,?,?) RETURNING *",ROW,UUID.randomUUID(),game,input.title().strip(),date);
 }
 @PostMapping("/{id}") public Update edit(@PathVariable UUID game,@PathVariable UUID id,@RequestBody Input input){
  games.get(game);var date=validate(input);
  if(input.version()==null||input.version()<0)throw new ApiException(400,"INVALID_UPDATE","Include the current update version.");
  var saved=jdbc.query("UPDATE playersignal.game_update SET title=?,released_on=?,version=version+1,updated_at=now() WHERE id=? AND game_id=? AND version=? RETURNING *",ROW,input.title().strip(),date,id,game,input.version());
  if(!saved.isEmpty())return saved.getFirst();
  if(jdbc.queryForObject("SELECT count(*) FROM playersignal.game_update WHERE id=? AND game_id=?",Long.class,id,game)==0)throw new ApiException(404,"UPDATE_NOT_FOUND","This update does not exist in the selected game.");
  throw new ApiException(409,"UPDATE_CHANGED","This update changed. Reload the list before editing again.");
 }
}
