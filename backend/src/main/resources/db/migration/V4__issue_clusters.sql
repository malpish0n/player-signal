CREATE TABLE steampulse.issue_snapshot (
    game_id UUID PRIMARY KEY REFERENCES steampulse.game(id) ON DELETE CASCADE,
    built_at TIMESTAMPTZ NOT NULL,
    input_fingerprint TEXT NOT NULL,
    algorithm TEXT NOT NULL,
    threshold DOUBLE PRECISION NOT NULL,
    source_count INTEGER NOT NULL
);
CREATE TABLE steampulse.issue_cluster (
    id UUID PRIMARY KEY,
    game_id UUID NOT NULL REFERENCES steampulse.game(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    category TEXT NOT NULL,
    centroid JSONB NOT NULL,
    metrics JSONB NOT NULL
);
CREATE INDEX issue_game ON steampulse.issue_cluster(game_id);
CREATE TABLE steampulse.issue_mention (
    issue_id UUID NOT NULL REFERENCES steampulse.issue_cluster(id) ON DELETE CASCADE,
    analysis_id UUID NOT NULL REFERENCES steampulse.review_analysis(id),
    similarity DOUBLE PRECISION NOT NULL CHECK (similarity BETWEEN -1 AND 1),
    PRIMARY KEY (issue_id, analysis_id),
    UNIQUE(analysis_id)
);
