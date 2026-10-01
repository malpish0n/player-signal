# Remaining delivery scope

Owner instruction (2026-10-01): proceed in coherent steps and commit each; do not run automated tests or builds. Record manual/static verification honestly. The running Docker images cannot verify new Java/Next production code until a later build is authorized. Prior passing checks do not validate new changes.

Owner constraint: no paid OpenAI calls. Hosting/domain, Stripe and email accounts will be set up later. Prepare local functionality and deployment/integration boundaries; do not claim external integrations are live or publish anything.

| Package | Status |
| --- | --- |
| Durable analysis quota / usage counters | Implemented; static review only |
| Free local analysis and optional local model | Implemented adapters; static review only; local model installation/quality evaluation pending |
| Issue analytics, scoring and exploration | Per-issue timeline/share/priority and manual workflow implemented; static review only; remaining filters/semantic quality pending |
| Full patch comparison | Category/issue shares and timeline implemented; static review only |
| Saved weekly reports / evidence summaries | Deterministic snapshots implemented; static review only; AI executive synthesis not implemented |
| Onboarding and UI completion | Pending |
| Public landing and legal drafts | Pending |
| Deployment, monitoring, backups | Pending configuration; hosting deferred by owner |
| Billing | External integration deferred by owner |
| Scheduled work and notifications | Pending; email delivery deferred by owner |
| Teams / operations / saved product preferences | Pending |
| Data controls and sharing | Pending |

A–E are implemented alpha slices, not completed production acceptance. The 3–5 game quality gate, semantic clustering evaluation and end-to-end release verification remain open. Rules-based classification must never be labeled as AI or synthetic fixtures injected into live data.
