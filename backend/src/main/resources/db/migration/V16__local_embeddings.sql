CREATE TABLE playersignal.issue_embedding (
 analysis_id UUID NOT NULL REFERENCES playersignal.review_analysis(id) ON DELETE CASCADE,
 model_key TEXT NOT NULL,
 input_hash TEXT NOT NULL,
 vector JSONB NOT NULL CHECK(jsonb_typeof(vector)='array'),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 PRIMARY KEY(analysis_id,model_key,input_hash)
);
