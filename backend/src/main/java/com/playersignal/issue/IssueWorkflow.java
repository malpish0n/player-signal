package com.playersignal.issue;
import com.playersignal.shared.ApiException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
@Service
public class IssueWorkflow {
 public record State(String status) {}
 private final JdbcTemplate jdbc;public IssueWorkflow(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public static String key(String category,String title){return category+":"+IssueEngine.normalize(title);}
 private String key(UUID game,UUID issue){return jdbc.query("SELECT category,title FROM playersignal.issue_cluster WHERE game_id=? AND id=?",(rs,n)->key(rs.getString(1),rs.getString(2)),game,issue).stream().findFirst().orElseThrow(()->new ApiException(404,"ISSUE_NOT_FOUND","Issue not found in this game."));}
 public Map<String,String> states(UUID game){var result=new HashMap<String,String>();jdbc.query("SELECT issue_key,status FROM playersignal.issue_workflow WHERE game_id=?",rs->{result.put(rs.getString(1),rs.getString(2));},game);return result;}
 public State get(UUID game,UUID issue){return new State(states(game).getOrDefault(key(game,issue),"OPEN"));}
 public State save(UUID game,UUID issue,State input){if(input.status()==null||!Set.of("OPEN","WATCHING","IMPROVING","RESOLVED","IGNORED").contains(input.status()))throw new ApiException(400,"INVALID_STATUS","Choose a supported issue status.");String key=key(game,issue);jdbc.update("INSERT INTO playersignal.issue_workflow(game_id,issue_key,status) VALUES (?,?,?) ON CONFLICT(game_id,issue_key) DO UPDATE SET status=EXCLUDED.status,updated_at=now()",game,key,input.status());return input;}
}
