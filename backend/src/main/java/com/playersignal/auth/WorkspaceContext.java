package com.playersignal.auth;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.playersignal.shared.ApiException;

@Component
public class WorkspaceContext {
    public static final UUID LOCAL = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String ACCESS_ATTRIBUTE = WorkspaceContext.class.getName() + ".access";
    private record Access(AccountPrincipal principal, String role) {}
    private final boolean enabled;
    private final JdbcTemplate jdbc;

    public WorkspaceContext(@Value("${playersignal.auth.enabled:false}") boolean enabled, JdbcTemplate jdbc) {
        this.enabled = enabled;
        this.jdbc = jdbc;
    }
    public boolean enabled() { return enabled; }
    public UUID current() { return enabled ? access().principal().workspaceId() : LOCAL; }
    public UUID user() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AccountPrincipal principal) return principal.id();
        throw new ApiException(401, "AUTH_REQUIRED", "Sign in to manage your account.");
    }
    public AccountPrincipal principal(UUID user) { return lookup(user).principal(); }
    public String role() { return enabled ? access().role() : "OWNER"; }
    private Access lookup(UUID user) {
        return jdbc.query("""
            SELECT u.id,u.email,w.id AS workspace_id,w.name,m.role
            FROM playersignal.app_user u
            JOIN playersignal.workspace_member m ON m.user_id=u.id AND m.workspace_id=u.workspace_id
            JOIN playersignal.workspace w ON w.id=m.workspace_id WHERE u.id=?
            """, (rs, n) -> new Access(new AccountPrincipal(rs.getObject("id", UUID.class), rs.getString("email"),
                rs.getObject("workspace_id", UUID.class), rs.getString("name")), rs.getString("role")), user)
            .stream().findFirst().orElseThrow(() -> new ApiException(401, "AUTH_REQUIRED", "Account or workspace access changed. Sign in again."));
    }
    private Access access() {
        UUID user = user();
        // One request uses one workspace/role pair, even if another tab switches the active workspace.
        // No session cache: membership removal and role changes take effect on the next request.
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            var request = attributes.getRequest();
            if (request.getAttribute(ACCESS_ATTRIBUTE) instanceof Access cached && cached.principal().id().equals(user)) return cached;
            var access = lookup(user);
            request.setAttribute(ACCESS_ATTRIBUTE, access);
            return access;
        }
        return lookup(user);
    }
    public void requireOwner() {
        if (!role().equals("OWNER")) throw new ApiException(403, "OWNER_REQUIRED", "Only the workspace owner can perform this action.");
    }
    public void requireWrite() {
        if (!role().equals("OWNER") && !role().equals("ADMIN"))
            throw new ApiException(403, "READ_ONLY_MEMBER", "Members have read-only access. Ask an owner or admin to make changes.");
    }
}
