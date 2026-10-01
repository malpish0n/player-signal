CREATE TABLE playersignal.alert_policy (
 game_id UUID PRIMARY KEY REFERENCES playersignal.game(id) ON DELETE CASCADE,
 enabled BOOLEAN NOT NULL DEFAULT false,
 last_checked_at TIMESTAMPTZ,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
