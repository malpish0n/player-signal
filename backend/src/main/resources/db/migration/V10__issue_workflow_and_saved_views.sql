CREATE TABLE playersignal.issue_workflow (
 game_id UUID NOT NULL REFERENCES playersignal.game(id) ON DELETE CASCADE,
 issue_key TEXT NOT NULL,
 status TEXT NOT NULL CHECK(status IN ('OPEN','WATCHING','IMPROVING','RESOLVED','IGNORED')),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 PRIMARY KEY(game_id,issue_key)
);
CREATE TABLE playersignal.saved_view (
 id UUID PRIMARY KEY,
 game_id UUID NOT NULL REFERENCES playersignal.game(id) ON DELETE CASCADE,
 name VARCHAR(80) NOT NULL,
 path VARCHAR(2048) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX saved_view_game ON playersignal.saved_view(game_id,created_at DESC);
