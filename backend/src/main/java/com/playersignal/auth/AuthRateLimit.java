package com.playersignal.auth;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;
import com.playersignal.shared.ApiException;
@Component
public class AuthRateLimit {
 private record Window(long expires,int count){}
 private final Map<String,Window> attempts=new HashMap<>();
 public synchronized void check(String ip,String email){long now=System.currentTimeMillis();attempts.entrySet().removeIf(e->e.getValue().expires()<now);if(attempts.size()>10000)throw limited();consume("ip:"+ip,30,now);consume("email:"+(email==null?"":email.strip().toLowerCase(Locale.ROOT)),8,now);}
 private void consume(String key,int limit,long now){var w=attempts.get(key);if(w!=null&&w.count()>=limit)throw limited();attempts.put(key,new Window(w==null?now+Duration.ofMinutes(5).toMillis():w.expires(),w==null?1:w.count()+1));}
 private ApiException limited(){return new ApiException(429,"AUTH_RATE_LIMIT","Too many account attempts. Wait five minutes and retry.");}
}
