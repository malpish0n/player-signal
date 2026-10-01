package com.playersignal.auth;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import com.playersignal.shared.ApiException;
@Component
public class WorkspaceContext {
 public static final UUID LOCAL=UUID.fromString("00000000-0000-0000-0000-000000000001");
 private final boolean enabled;private final org.springframework.jdbc.core.JdbcTemplate jdbc;
 public WorkspaceContext(@Value("${playersignal.auth.enabled:false}")boolean enabled,org.springframework.jdbc.core.JdbcTemplate jdbc){this.enabled=enabled;this.jdbc=jdbc;}
 public boolean enabled(){return enabled;}
 public UUID current(){if(!enabled)return LOCAL;var auth=SecurityContextHolder.getContext().getAuthentication();if(auth!=null&&auth.getPrincipal() instanceof AccountPrincipal p)return principal(p.id()).workspaceId();throw new ApiException(401,"AUTH_REQUIRED","Sign in to access your workspace.");}
 public UUID user(){var auth=SecurityContextHolder.getContext().getAuthentication();if(auth!=null&&auth.getPrincipal() instanceof AccountPrincipal p)return p.id();throw new ApiException(401,"AUTH_REQUIRED","Sign in to manage your account.");}
 public AccountPrincipal principal(UUID user){return jdbc.query("SELECT u.id,u.email,w.id AS workspace_id,w.name FROM playersignal.app_user u JOIN playersignal.workspace_member m ON m.user_id=u.id AND m.workspace_id=u.workspace_id JOIN playersignal.workspace w ON w.id=m.workspace_id WHERE u.id=?",(rs,n)->new AccountPrincipal(rs.getObject("id",UUID.class),rs.getString("email"),rs.getObject("workspace_id",UUID.class),rs.getString("name")),user).stream().findFirst().orElseThrow(()->new ApiException(401,"AUTH_REQUIRED","Account or workspace access changed. Sign in again."));}
 public String role(){if(!enabled)return "OWNER";return jdbc.queryForObject("SELECT role FROM playersignal.workspace_member WHERE workspace_id=? AND user_id=?",String.class,current(),user());}
 public void requireOwner(){if(!role().equals("OWNER"))throw new ApiException(403,"OWNER_REQUIRED","Only the workspace owner can perform this action.");}
 public void requireWrite(){if(role().equals("MEMBER"))throw new ApiException(403,"READ_ONLY_MEMBER","Members have read-only access. Ask an owner or admin to make changes.");}
}

