# Local alpha HTTP API

OpenAPI contract: `GET http://localhost:8080/openapi.json` (source: `backend/src/main/resources/static/openapi.json`). UUIDs identify local resources; timestamps use UTC ISO 8601. Accounts are opt-in; the default remains a loopback-bound local stack.

| Method | Path | Behavior |
| --- | --- | --- |
| GET | /api/games | List connected games |
| POST | /api/games | Resolve and upsert `{"steamApp":"620"}` or a full HTTPS Steam store URL; returns 200 for both new/existing game |
| GET | /api/games/{id} | Game metadata |
| POST | /api/games/{id}/sync | Returns 202 with a RUNNING job; 409 if busy or the same game is already syncing |
| GET | /api/games/{id}/sync/latest | Latest run; 204 if never synced |
| GET | /api/games/{id}/reviews | Paginated persisted source reviews |

Review query parameters: `page` (zero-based, default 0, max 100000), `size` (1–100, default 20), optional `language` (Steam code, e.g. `english`) and `votedUp` (`true`/`false`). Ordering: Steam creation timestamp descending, UUID tie-breaker. Returns `{items,total,page,size}`. `playtimeMinutes` is lifetime playtime in minutes; text is preserved exactly.

Runs expose status, timestamps, fetched/inserted/updated counts, resume cursor and error. Counts are per run; unchanged source records contribute to fetched only. COMPLETED means an empty final page was reached; PARTIAL means the configured page budget was reached, and the next sync resumes. FAILED also resumes at the last committed cursor. A sync after COMPLETED starts a new scan ordered by update time to observe edits.

Error shape: `{code,message,requestId}`. Invalid input is 400; missing game 404; conflicting import 409; Steam failure 502; unavailable worker 503. Previously committed pages remain readable on failure.

The frontend forwards only allowlisted game, demo and authentication routes through its same-origin `/api` handler; it rejects cross-origin POSTs. No wildcard CORS or browser-held backend secrets.

Health endpoints remain `/actuator/health`, `/actuator/health/readiness` (includes PostgreSQL) and `/actuator/health/liveness` (process only).

## Milestone C: review analysis

- `GET /api/games/{id}/analysis`: availability, provider/model/prompt version, per-batch cap, current-input counts and latest run. Unconfigured analysis is a successful read with `available:false`.
- `POST /api/games/{id}/analysis?retryFailed=false`: 202 with a background run. Only missing current versions are selected by default; `retryFailed=true` also includes failures. 409 when busy; 503 when no live provider is configured. Batch selection spans all reviews, independent of UI filters.
- `GET /api/games/{id}/reviews/{reviewId}/analyses`: version history containing source text, language and input hash. Wrong-game review IDs return 404.
- `GET /api/analysis/demo`: four explicitly synthetic fixture reviews and deterministic outputs; no persistence or external provider call.

Review list items now contain nullable `analysis`, matched to the current text/language hash, provider, model and prompt version. Status is RUNNING, SUCCEEDED, FAILED or SKIPPED; only SUCCEEDED carries a classification. Error, skip reason, attempts, cache origin, actual response model and timestamp support inspection.

Classification contains sentiment, primaryCategory, severity, confidence (0–1), normalizedIssue, isActionable, isLikelyBug, evidence and tags. Evidence is a verbatim substring of the source, preserving language. The server rejects unsupported fields/types and invalid quotes. Model confidence is not calibrated.

Run states: RUNNING, COMPLETED, PARTIAL (unprocessed input remains), COMPLETED_WITH_ERRORS, FAILED. Counts are current-version counts; historical analyses do not inflate them. Successful unchanged reviews are not reclassified. Explicit retries reuse the failed version row and accumulate attempt counts, while preserving completed run counters.

## Milestone D: issue intelligence

- `GET /api/games/{game}/issues?page=0&size=20`: paginated issue summaries ordered by severity score, mentions, ID. Includes snapshot metadata, current eligible count and staleness. Missing snapshot has `builtAt:null`.
- `POST /api/games/{game}/issues/rebuild`: synchronously rebuild up to 2000 current actionable analyses, returning the first issue page. Uses local lexical embeddings, no external calls. 409 for concurrent rebuild; 422 over the cap; 503 on failure with the old snapshot preserved.
- `GET /api/games/{game}/issues/{issue}?page=0&size=20`: metrics and exact evidence snapshots, IDs, classification, model/prompt and similarity. Wrong-game IDs return 404. Page size is 1–100.
- `GET /api/issues/demo`: fixed six-review synthetic corpus through the same engine, three groups, no writes. Reference time is explicitly supplied.

Metrics are calculated as of `snapshot.builtAt`. Mention count counts each linked review analysis once. First/last seen use review creation timestamps. `negativeRatio` counts NEGATIVE and VERY_NEGATIVE classifications divided by mentions. `severityScore` is mean severity (LOW=25, MEDIUM=50, HIGH=75, CRITICAL=100); `confidence` is mean classifier confidence, not a calibrated probability. Recent mentions use [asOf−7d,asOf], previous mentions [asOf−14d,asOf−7d). Velocity is 100×(recent−previous)/previous; null means a zero baseline, not zero growth. Future-dated reviews do not enter velocity windows.

Snapshot metrics do not advance automatically with wall-clock time. Rebuild to roll the windows forward. Input edits, new actionable analyses and model/prompt/algorithm changes flag the snapshot stale. Evidence remains the exact analysis input even when the live review has changed. Issue status is currently OPEN; workflow transitions are deferred. Rebuild identifiers are stable for unchanged inputs but may change if the representative analysis changes; clients should handle 404 and return to the list.

## Milestone E: overview and exploration

`GET /api/games/{id}/overview?days=30` accepts 7, 30 or 90 days and reads a consistent repeatable-read snapshot. `periodStart` and `asOf` bound [asOf−N days,asOf); the comparison uses the immediately preceding equal-duration interval. Future timestamps and the exclusive upper boundary are excluded. Daily buckets are UTC, explicitly zero-filled; the first/last day may be partial. Empty recommendation ratios and zero-baseline changes are null. `positiveChangePoints` is percentage points, not relative growth.

Recommendation metrics come from Steam votes. Category counts include only successful analyses matching the current text hash, provider, model and prompt in the selected period. All-imported analysis coverage and issue snapshots are returned separately and clearly labeled in the UI. Issue metrics keep their own calculation time and two 7-day windows; changing the overview period does not rebuild issues. High-severity count uses snapshot mean severity >=75.

`GET /api/overview/demo?days=30` uses the existing six synthetic complaint fixtures plus four explicitly synthetic positive recommendations at fixed dates, with a fixed reference time. It does not seed live data or call providers. The response has `mode:SYNTHETIC_DEMO`.

Review browsing adds `q` (case-insensitive literal substring, max 256 characters). Issue listing adds `minSeverity` (0–100). Pagination and filtering are server-side and URL-addressable.

## Account and workspace protocol

- `GET /api/auth/session`: `{enabled,user,csrfToken}`. User is null or `{id,email,workspaceId,workspaceName}`. In enabled mode an anonymous session supplies a CSRF token.
- `POST /api/auth/register`: `{email,password,workspaceName}` creates a private workspace and signs in.
- `POST /api/auth/login`: `{email,password}` signs in; invalid credentials return 401.
- `POST /api/auth/logout`: invalidates the session and expires the cookie.

With accounts enabled, send the `PLAYERSIGNAL_SESSION` cookie and `X-CSRF-TOKEN` from the session response on every POST, including login/registration/logout. Login/registration rotate the session and return a fresh token. Fetch the session again after logout. The same-origin frontend proxy forwards only this cookie and CSRF header; browser requests retain same-origin protection. Missing/invalid CSRF returns 403; unauthenticated game access returns 401; throttled authentication returns 429. Registration/login return 409 in local mode.

Game lists and new connections belong to the current workspace. The same Steam App ID may exist independently in different workspaces. Every nested game route checks workspace ownership, including reads, jobs and analysis history; another workspace's identifier returns 404. Existing local data is isolated from newly registered accounts. Public synthetic demo routes remain available. Session and CSRF values are null/disabled in local mode.
