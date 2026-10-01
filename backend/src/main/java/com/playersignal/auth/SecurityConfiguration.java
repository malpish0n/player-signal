package com.playersignal.auth;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.*;
@Configuration
public class SecurityConfiguration {
 @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder(12);}
 @Bean HttpSessionSecurityContextRepository contextRepository(){return new HttpSessionSecurityContextRepository();}
 @Bean HttpSessionCsrfTokenRepository csrfRepository(){return new HttpSessionCsrfTokenRepository();}
 // Authentication is JSON-based through AuthService; disable Boot's generated default account.
 @Bean UserDetailsService unusedUserDetailsService(){return name->{throw new UsernameNotFoundException("Use account login.");};}
 @Bean SecurityFilterChain security(HttpSecurity http,WorkspaceContext workspace,JdbcTemplate jdbc,HttpSessionSecurityContextRepository context,HttpSessionCsrfTokenRepository csrf)throws Exception{
  http.formLogin(form->form.disable()).httpBasic(basic->basic.disable()).logout(logout->logout.disable())
   .requestCache(cache->cache.disable()).securityContext(c->c.securityContextRepository(context).requireExplicitSave(true))
   .exceptionHandling(e->e.authenticationEntryPoint((req,res,error)->{res.setStatus(401);res.setContentType("application/json");res.getWriter().write("{\"code\":\"AUTH_REQUIRED\",\"message\":\"Sign in to access your workspace.\"}");})
    .accessDeniedHandler((req,res,error)->{res.setStatus(403);res.setContentType("application/json");res.getWriter().write("{\"code\":\"REQUEST_FORBIDDEN\",\"message\":\"Your session or request token expired. Reload and retry.\"}");}));
  if(workspace.enabled()){
   http.csrf(c->c.ignoringRequestMatchers("/api/billing/webhook").csrfTokenRepository(csrf).csrfTokenRequestHandler(new org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler())).authorizeHttpRequests(a->a
    .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/billing/webhook").permitAll()
    .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/shared-reports/*").permitAll()
    .requestMatchers("/actuator/health/**","/api/auth/session","/api/auth/register","/api/auth/login","/api/auth/logout","/api/analysis/demo","/api/issues/demo","/api/overview/demo","/error").permitAll()
    .anyRequest().authenticated());
  }else{http.csrf(c->c.disable()).authorizeHttpRequests(a->a.anyRequest().permitAll());}
  http.addFilterBefore(new WorkspaceFilter(jdbc,workspace),AuthorizationFilter.class);
  return http.build();
 }
}
