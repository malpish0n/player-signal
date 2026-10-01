package com.playersignal.shared;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
@Component @Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTraceFilter extends OncePerRequestFilter {
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
  String id=UUID.randomUUID().toString();long start=System.nanoTime();MDC.put("requestId",id);response.setHeader("X-Request-ID",id);
  try{chain.doFilter(request,response);}finally{if(!request.getRequestURI().startsWith("/actuator/health"))LoggerFactory.getLogger(getClass()).info("request method={} status={} durationMs={}",request.getMethod(),response.getStatus(),(System.nanoTime()-start)/1000000);MDC.remove("requestId");}
 }
}
