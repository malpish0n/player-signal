CREATE TABLE playersignal.report (
 id UUID PRIMARY KEY,
 game_id UUID NOT NULL REFERENCES playersignal.game(id) ON DELETE CASCADE,
 request_id UUID NOT NULL,
 request_key TEXT NOT NULL,
 type TEXT NOT NULL CHECK(type IN ('WEEKLY','PATCH')),
 period_start DATE NOT NULL,
 period_end DATE NOT NULL,
 payload JSONB NOT NULL CHECK(octet_length(payload::text)<=2097152),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(game_id,request_id)
);
CREATE INDEX report_game_created ON playersignal.report(game_id,created_at DESC,id);
