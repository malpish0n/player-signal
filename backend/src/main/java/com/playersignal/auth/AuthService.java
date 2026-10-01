package com.playersignal.auth;
import com.playersignal.shared.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class AuthService {
 private final JdbcTemplate jdbc;private final PasswordEncoder passwords;private final WorkspaceContext context;private final String dummyHash;
 public AuthService(JdbcTemplate jdbc,PasswordEncoder passwords,WorkspaceContext context){this.jdbc=jdbc;this.passwords=passwords;this.context=context;this.dummyHash=passwords.encode(UUID.randomUUID().toString());}
 private void enabled(){if(!context.enabled())throw new ApiException(409,"LOCAL_MODE","Accounts are disabled in local mode. Enable AUTH_ENABLED to use isolated accounts.");}
 private String email(String email){if(email==null)throw invalid();String value=email.strip().toLowerCase(Locale.ROOT);if(value.length()>254||!value.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))throw invalid();return value;}
 private ApiException invalid(){return new ApiException(400,"INVALID_ACCOUNT","Use a valid email and a password of 12–72 UTF-8 bytes.");}
 @Transactional public AccountPrincipal register(String email,String password,String workspaceName){enabled();String address=email(email);if(password==null||password.length()<12||password.getBytes(StandardCharsets.UTF_8).length>72)throw invalid();if(workspaceName==null||workspaceName.isBlank()||workspaceName.strip().length()>80)throw new ApiException(400,"INVALID_WORKSPACE","Workspace name must contain 1–80 characters.");
  UUID user=UUID.randomUUID(),workspace=UUID.randomUUID();String name=workspaceName.strip();String hash=passwords.encode(password);
  try{jdbc.update("INSERT INTO playersignal.workspace(id,name) VALUES (?,?)",workspace,name);jdbc.update("INSERT INTO playersignal.app_user(id,email,password_hash,workspace_id) VALUES (?,?,?,?)",user,address,hash,workspace);}catch(DuplicateKeyException e){throw new ApiException(409,"ACCOUNT_UNAVAILABLE","An account cannot be created with these details. Try signing in.");}
  return new AccountPrincipal(user,address,workspace,name);
 }
 public AccountPrincipal login(String email,String password){enabled();String address=email(email);if(password==null||password.getBytes(StandardCharsets.UTF_8).length>72)throw new ApiException(401,"INVALID_CREDENTIALS","Email or password is incorrect.");
  var rows=jdbc.query("SELECT u.id,u.email,u.password_hash,u.workspace_id,w.name FROM playersignal.app_user u JOIN playersignal.workspace w ON w.id=u.workspace_id WHERE u.email=?",(rs,n)->new Stored(new AccountPrincipal(rs.getObject("id",UUID.class),rs.getString("email"),rs.getObject("workspace_id",UUID.class),rs.getString("name")),rs.getString("password_hash")),address);
  String hash=rows.isEmpty()?dummyHash:rows.getFirst().hash();boolean valid=passwords.matches(password,hash);if(!valid||rows.isEmpty())throw new ApiException(401,"INVALID_CREDENTIALS","Email or password is incorrect.");return rows.getFirst().principal();
 }
 private record Stored(AccountPrincipal principal,String hash){}
}
