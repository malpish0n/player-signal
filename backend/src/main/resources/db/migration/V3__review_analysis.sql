-- Hash only analyzer inputs, not changing helpful-vote or playtime metadata.
CREATE VIEW steampulse.review_input AS
SELECT r.*, encode(sha256(convert_to(json_build_array(language, review_text)::text, 'UTF8')), 'hex') AS input_hash
FROM steampulse.review r;

CREATE TABLE steampulse.analysis_run (
    id UUID PRIMARY KEY,
    game_id UUID NOT NULL REFERENCES steampulse.game(id) ON DELETE CASCADE,
    provider TEXT NOT NULL,
    model TEXT NOT NULL,
    prompt_version TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('RUNNING', 'COMPLETED', 'PARTIAL', 'COMPLETED_WITH_ERRORS', 'FAILED')),
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ,
    processed INTEGER NOT NULL DEFAULT 0,
    succeeded INTEGER NOT NULL DEFAULT 0,
    skipped INTEGER NOT NULL DEFAULT 0,
    failed INTEGER NOT NULL DEFAULT 0,
    cached INTEGER NOT NULL DEFAULT 0,
    error TEXT,
    CHECK ((status = 'RUNNING') = (finished_at IS NULL)),
    CHECK (processed = succeeded + skipped + failed),
    CHECK (succeeded >= 0 AND skipped >= 0 AND failed >= 0 AND cached BETWEEN 0 AND succeeded)
);
CREATE UNIQUE INDEX analysis_one_running ON steampulse.analysis_run(game_id) WHERE status='RUNNING';
CREATE INDEX analysis_run_latest ON steampulse.analysis_run(game_id, started_at DESC);

CREATE TABLE steampulse.review_analysis (
    id UUID PRIMARY KEY,
    review_id UUID NOT NULL REFERENCES steampulse.review(id) ON DELETE CASCADE,
    run_id UUID NOT NULL REFERENCES steampulse.analysis_run(id),
    input_hash TEXT NOT NULL,
    source_text TEXT NOT NULL,
    source_language TEXT NOT NULL,
    provider TEXT NOT NULL,
    model TEXT NOT NULL,
    prompt_version TEXT NOT NULL,
    response_model TEXT,
    status TEXT NOT NULL CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED', 'SKIPPED')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    result JSONB,
    error TEXT,
    skip_reason TEXT,
    cached_from UUID REFERENCES steampulse.review_analysis(id),
    input_tokens BIGINT NOT NULL DEFAULT 0 CHECK (input_tokens >= 0),
    output_tokens BIGINT NOT NULL DEFAULT 0 CHECK (output_tokens >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(review_id, input_hash, provider, model, prompt_version),
    CHECK ((status='SUCCEEDED') = (result IS NOT NULL))
);
CREATE INDEX analysis_cache ON steampulse.review_analysis(input_hash, provider, model, prompt_version) WHERE status='SUCCEEDED';
