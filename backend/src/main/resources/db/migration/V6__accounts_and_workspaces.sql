CREATE TABLE playersignal.workspace (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- Existing local data is quarantined from self-service accounts.
INSERT INTO playersignal.workspace(id,name) VALUES ('00000000-0000-0000-0000-000000000001','Local workspace');
CREATE TABLE playersignal.app_user (
    id UUID PRIMARY KEY,
    email TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    workspace_id UUID NOT NULL REFERENCES playersignal.workspace(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE playersignal.game ADD COLUMN workspace_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001' REFERENCES playersignal.workspace(id);
ALTER TABLE playersignal.game DROP CONSTRAINT game_steam_app_id_key;
ALTER TABLE playersignal.game ADD CONSTRAINT game_workspace_app_unique UNIQUE(workspace_id,steam_app_id);
CREATE INDEX game_workspace ON playersignal.game(workspace_id,created_at DESC);
