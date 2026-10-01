CREATE TABLE playersignal.game_automation (
 game_id UUID PRIMARY KEY REFERENCES playersignal.game(id) ON DELETE CASCADE,
 enabled BOOLEAN NOT NULL DEFAULT false,
 interval_hours INTEGER NOT NULL DEFAULT 24 CHECK(interval_hours BETWEEN 1 AND 168),
 analyze_local BOOLEAN NOT NULL DEFAULT false,
 weekly_report BOOLEAN NOT NULL DEFAULT false,
 next_sync_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 next_report_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 phase TEXT NOT NULL DEFAULT 'IDLE' CHECK(phase IN ('IDLE','FETCHING','ANALYZING','GROUPING')),
 run_id UUID,
 last_error TEXT,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE playersignal.notification (
 id UUID PRIMARY KEY,
 game_id UUID NOT NULL REFERENCES playersignal.game(id) ON DELETE CASCADE,
 event_key TEXT NOT NULL,
 title TEXT NOT NULL,
 message TEXT NOT NULL,
 read_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(game_id,event_key)
);
CREATE INDEX notification_game_time ON playersignal.notification(game_id,created_at DESC);
