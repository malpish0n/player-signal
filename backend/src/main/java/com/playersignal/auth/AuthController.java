package com.playersignal.auth;
import jakarta.servlet.http.*;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/auth")
public class AuthController {
 public record Credentials(String email,String password,String workspaceName){}
 public record Session(boolean enabled,AccountPrincipal user,String csrfToken){}
 private final AuthService service;private final WorkspaceContext workspace;private final HttpSessionSecurityContextRepository contexts;private final HttpSessionCsrfTokenRepository csrf;private final AuthRateLimit rate;
 public AuthController(AuthService service,WorkspaceContext workspace,HttpSessionSecurityContextRepository contexts,HttpSessionCsrfTokenRepository csrf,AuthRateLimit rate){this.service=service;this.workspace=workspace;this.contexts=contexts;this.csrf=csrf;this.rate=rate;}
 @GetMapping("/session") public Session session(HttpServletRequest request,HttpServletResponse response){var auth=SecurityContextHolder.getContext().getAuthentication();var user=auth!=null&&auth.getPrincipal() instanceof AccountPrincipal p?p:null;if(user!=null){try{user=workspace.principal(user.id());}catch(com.playersignal.shared.ApiException revoked){user=null;}}return new Session(workspace.enabled(),user,workspace.enabled()?token(request,response):null);}
 private String token(HttpServletRequest request,HttpServletResponse response){var value=csrf.loadToken(request);if(value==null){value=csrf.generateToken(request);csrf.saveToken(value,request,response);}return value.getToken();}
 @PostMapping("/register") public Session register(@RequestBody Credentials body,HttpServletRequest request,HttpServletResponse response){rate.check(request.getRemoteAddr(),body.email());return signIn(service.register(body.email(),body.password(),body.workspaceName()),request,response);}
 @PostMapping("/login") public Session login(@RequestBody Credentials body,HttpServletRequest request,HttpServletResponse response){rate.check(request.getRemoteAddr(),body.email());return signIn(service.login(body.email(),body.password()),request,response);}
 private Session signIn(AccountPrincipal user,HttpServletRequest request,HttpServletResponse response){var previous=request.getSession(false);if(previous!=null)previous.invalidate();var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(new UsernamePasswordAuthenticationToken(user,null,List.of(new SimpleGrantedAuthority("ROLE_USER"))));SecurityContextHolder.setContext(context);contexts.saveContext(context,request,response);return new Session(true,user,token(request,response));}
 @PostMapping("/logout") public Session logout(HttpServletRequest request,HttpServletResponse response){var previous=request.getSession(false);if(previous!=null)previous.invalidate();SecurityContextHolder.clearContext();response.addHeader("Set-Cookie","PLAYERSIGNAL_SESSION=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax");return new Session(workspace.enabled(),null,null);}
}
