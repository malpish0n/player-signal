CREATE TABLE playersignal.workspace_billing (
 workspace_id UUID PRIMARY KEY REFERENCES playersignal.workspace(id) ON DELETE CASCADE,
 customer_id TEXT UNIQUE,
 subscription_id TEXT UNIQUE,
 plan TEXT NOT NULL DEFAULT 'FREE' CHECK(plan IN ('FREE','INDIE','STUDIO')),
 status TEXT NOT NULL DEFAULT 'none',
 period_end TIMESTAMPTZ,
 grace_until TIMESTAMPTZ,
 verified_at TIMESTAMPTZ,
 checkout_key UUID,
 checkout_plan TEXT,
 checkout_expires_at TIMESTAMPTZ,
 checkout_id TEXT,
 checkout_url TEXT,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE playersignal.billing_event (
 id TEXT PRIMARY KEY,
 type TEXT NOT NULL,
 customer_id TEXT,
 status TEXT NOT NULL DEFAULT 'RECEIVED' CHECK(status IN ('RECEIVED','PROCESSED','FAILED')),
 attempts INTEGER NOT NULL DEFAULT 0,
 error TEXT,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 processed_at TIMESTAMPTZ
);
CREATE INDEX billing_pending ON playersignal.billing_event(status,created_at);
