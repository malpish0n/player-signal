package com.playersignal.auth;
import java.io.Serializable;
import java.util.UUID;
public record AccountPrincipal(UUID id,String email,UUID workspaceId,String workspaceName) implements Serializable {}
