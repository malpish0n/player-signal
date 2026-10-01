CREATE TABLE playersignal.retention_policy (
 game_id UUID PRIMARY KEY REFERENCES playersignal.game(id) ON DELETE CASCADE,
 enabled BOOLEAN NOT NULL DEFAULT false,
 review_days INTEGER NOT NULL DEFAULT 0 CHECK(review_days IN (0,90,180,365)),
 report_days INTEGER NOT NULL DEFAULT 0 CHECK(report_days IN (0,30,90,180,365)),
 notification_days INTEGER NOT NULL DEFAULT 90 CHECK(notification_days IN (30,90,180,365)),
 last_run_at TIMESTAMPTZ,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- Keep copied classifications if retention removes their original cached source.
ALTER TABLE playersignal.review_analysis DROP CONSTRAINT review_analysis_cached_from_fkey;
ALTER TABLE playersignal.review_analysis ADD CONSTRAINT review_analysis_cached_from_fkey
 FOREIGN KEY(cached_from) REFERENCES playersignal.review_analysis(id) ON DELETE SET NULL;
