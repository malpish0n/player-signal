CREATE TABLE playersignal.game_update (
 id UUID PRIMARY KEY,
 game_id UUID NOT NULL REFERENCES playersignal.game(id) ON DELETE CASCADE,
 title VARCHAR(120) NOT NULL CHECK (length(trim(title)) > 0),
 released_on DATE NOT NULL CHECK (released_on BETWEEN DATE '0001-01-01' AND DATE '9999-12-31'),
 version INTEGER NOT NULL DEFAULT 0,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX game_update_dates ON playersignal.game_update(game_id, released_on DESC, id);
