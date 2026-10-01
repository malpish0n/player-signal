CREATE TABLE steampulse.game (
    id UUID PRIMARY KEY,
    steam_app_id BIGINT NOT NULL UNIQUE CHECK (steam_app_id BETWEEN 1 AND 4294967295),
    name TEXT NOT NULL,
    header_image_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE steampulse.review (
    id UUID PRIMARY KEY,
    game_id UUID NOT NULL REFERENCES steampulse.game(id),
    steam_recommendation_id TEXT NOT NULL,
    language TEXT NOT NULL,
    review_text TEXT NOT NULL,
    voted_up BOOLEAN NOT NULL,
    votes_up BIGINT NOT NULL CHECK (votes_up >= 0),
    playtime_minutes BIGINT NOT NULL CHECK (playtime_minutes >= 0),
    created_at_steam TIMESTAMPTZ NOT NULL,
    updated_at_steam TIMESTAMPTZ NOT NULL,
    raw_payload JSONB NOT NULL,
    imported_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (game_id, steam_recommendation_id)
);
CREATE INDEX review_game_date ON steampulse.review(game_id, created_at_steam DESC, id);
CREATE INDEX review_game_filters ON steampulse.review(game_id, language, voted_up);
CREATE TABLE steampulse.ingestion_run (
    id UUID PRIMARY KEY,
    game_id UUID NOT NULL REFERENCES steampulse.game(id),
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ,
    status TEXT NOT NULL CHECK (status IN ('RUNNING', 'COMPLETED', 'PARTIAL', 'FAILED')),
    fetched INTEGER NOT NULL DEFAULT 0,
    inserted INTEGER NOT NULL DEFAULT 0,
    updated INTEGER NOT NULL DEFAULT 0,
    next_cursor TEXT NOT NULL DEFAULT '*',
    error TEXT,
    CHECK (fetched >= 0 AND inserted >= 0 AND updated >= 0),
    CHECK ((status = 'RUNNING') = (finished_at IS NULL))
);
CREATE UNIQUE INDEX ingestion_one_active ON steampulse.ingestion_run(game_id) WHERE status = 'RUNNING';
CREATE INDEX ingestion_game_latest ON steampulse.ingestion_run(game_id, started_at DESC);
