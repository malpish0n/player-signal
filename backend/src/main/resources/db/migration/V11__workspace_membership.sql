CREATE TABLE playersignal.workspace_member (
 workspace_id UUID NOT NULL REFERENCES playersignal.workspace(id) ON DELETE CASCADE,
 user_id UUID NOT NULL REFERENCES playersignal.app_user(id) ON DELETE CASCADE,
 role TEXT NOT NULL CHECK(role IN ('OWNER','ADMIN','MEMBER')),
 PRIMARY KEY(workspace_id,user_id)
);
INSERT INTO playersignal.workspace_member(workspace_id,user_id,role) SELECT workspace_id,id,'OWNER' FROM playersignal.app_user;
CREATE TABLE playersignal.workspace_invite (
 id UUID PRIMARY KEY,
 workspace_id UUID NOT NULL REFERENCES playersignal.workspace(id) ON DELETE CASCADE,
 email TEXT NOT NULL,
 role TEXT NOT NULL CHECK(role IN ('ADMIN','MEMBER')),
 token_hash TEXT NOT NULL UNIQUE,
 expires_at TIMESTAMPTZ NOT NULL,
 accepted_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX invite_workspace ON playersignal.workspace_invite(workspace_id,expires_at);
