package com.playersignal.auth;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import com.playersignal.shared.ApiException;
@Component
public class WorkspaceContext {
 public static final UUID LOCAL=UUID.fromString("00000000-0000-0000-0000-000000000001");
 private final boolean enabled;
 public WorkspaceContext(@Value("${playersignal.auth.enabled:false}")boolean enabled){this.enabled=enabled;}
 public boolean enabled(){return enabled;}
 public UUID current(){if(!enabled)return LOCAL;var auth=SecurityContextHolder.getContext().getAuthentication();if(auth!=null&&auth.getPrincipal() instanceof AccountPrincipal p)return p.workspaceId();throw new ApiException(401,"AUTH_REQUIRED","Sign in to access your workspace.");}
}
