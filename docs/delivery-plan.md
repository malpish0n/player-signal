# Remaining delivery scope

Owner instruction (2026-10-01): proceed in coherent steps and commit each; do not run automated tests or builds. Static lint/type inspection is separate and produces no application build. Record manual/static verification honestly. The running Docker images cannot verify new Java/Next production code until a later build is authorized. Prior passing checks do not validate new changes.

Owner constraint: no paid OpenAI calls. Hosting/domain, Stripe and email accounts will be set up later. Prepare local functionality and deployment/integration boundaries; do not claim external integrations are live or publish anything.

| Package | Status |
| --- | --- |
| Durable analysis quota / usage counters | Implemented; static review only |
| Free local analysis and optional local model | Implemented adapters; static review only; local model installation/quality evaluation pending |
| Issue analytics, scoring and exploration | Per-issue timeline/share/priority and manual workflow implemented; static review only; category/status/issue filters implemented; semantic quality pending |
| Full patch comparison | Category/issue shares and timeline implemented; static review only |
| Saved weekly reports / evidence summaries | Deterministic snapshots implemented; static review only; AI executive synthesis not implemented |
| Onboarding and UI completion | Preview/confirm/import and guided processing implemented; E2E/accessibility/performance acceptance not run |
| Public landing and legal drafts | Implemented; owner/legal review and publication deferred |
| Deployment, monitoring, backups | Templates/scripts/runbook implemented; no deployment or restore drill; hosting deferred by owner |
| Billing | External integration deferred by owner |
| Scheduled work and notifications | Opt-in dispatcher/local analysis/weekly snapshots and in-app notices implemented; runtime verification and email/spike alerts pending |
| Teams / operations / saved product preferences | Membership, manual invites, workspace jobs, workflow and saved views implemented; security runtime acceptance pending |
| Data controls and sharing | Bounded export and explicit game/account deletion implemented; public share links and retention automation pending |

A–E are implemented alpha slices, not completed production acceptance. The 3–5 game quality gate, semantic clustering evaluation and end-to-end release verification remain open. Rules-based classification must never be labeled as AI or synthetic fixtures injected into live data.
