CREATE TABLE playersignal.usage_event (
 id UUID PRIMARY KEY,
 workspace_id UUID NOT NULL REFERENCES playersignal.workspace(id),
 game_id UUID REFERENCES playersignal.game(id) ON DELETE SET NULL,
 analysis_id UUID,
 action TEXT NOT NULL CHECK(action IN ('ANALYSIS','SYNC','GAME_LOOKUP')),
 model TEXT,
 status TEXT NOT NULL DEFAULT 'RESERVED' CHECK(status IN ('RESERVED','SUCCEEDED','FAILED')),
 input_tokens BIGINT NOT NULL DEFAULT 0 CHECK(input_tokens >= 0),
 output_tokens BIGINT NOT NULL DEFAULT 0 CHECK(output_tokens >= 0),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 finished_at TIMESTAMPTZ
);
CREATE INDEX usage_workspace_action_time ON playersignal.usage_event(workspace_id,action,created_at);
