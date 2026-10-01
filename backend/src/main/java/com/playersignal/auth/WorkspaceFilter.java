package com.playersignal.auth;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
/** All nested game routes inherit ownership enforcement before controllers/services run. */
public final class WorkspaceFilter extends OncePerRequestFilter {
 private static final Pattern GAME=Pattern.compile("^/api/games/([^/]+)(?:/.*)?$");
 private final JdbcTemplate jdbc;private final WorkspaceContext workspace;
 public WorkspaceFilter(JdbcTemplate jdbc,WorkspaceContext workspace){this.jdbc=jdbc;this.workspace=workspace;}
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
  var match=GAME.matcher(org.springframework.web.util.UrlPathHelper.defaultInstance.getPathWithinApplication(request));var auth=SecurityContextHolder.getContext().getAuthentication();
  if(!request.getServletPath().equals("/api/games/preview")&&match.matches()&&(!workspace.enabled()||(auth!=null&&auth.getPrincipal() instanceof AccountPrincipal))){
   UUID owner=workspace.current();
   UUID id;try{id=UUID.fromString(match.group(1));}catch(IllegalArgumentException e){response.sendError(400);return;}
   if(jdbc.queryForObject("SELECT count(*) FROM playersignal.game WHERE id=? AND workspace_id=?",Integer.class,id,owner)==0){response.setStatus(404);response.setContentType("application/json");response.getWriter().write("{\"code\":\"GAME_NOT_FOUND\",\"message\":\"Game not found in your workspace.\"}");return;}
  }
  chain.doFilter(request,response);
 }
}
