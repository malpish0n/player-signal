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

## Milestone F3: review CSV export

`GET /api/games/{id}/reviews/export?language=english&votedUp=false&q=crash` downloads all matching imported source reviews, across all pages. Filters and newest-first/UUID ordering match the explorer. The response is a single repeatable-read database snapshot; page and size parameters do not limit an export. Maximum 5000 rows and 10 MiB including BOM/header; exceeding either returns JSON 422 `EXPORT_TOO_LARGE`, with no truncated CSV. Invalid filters return 400, inaccessible games 404, and anonymous requests 401 when accounts are enabled.

Columns: review_id, steam_review_id, language, recommended, helpful_votes, playtime_minutes, created_at_utc, updated_at_utc, review_text. No author identifiers, raw JSON or AI output. UTF-8 BOM, comma separator, quoted fields with doubled quotes and CRLF record separators; embedded text newlines are retained. Potential formula prefixes (`=`, `+`, `-`, `@`, including after whitespace; leading tabs/newlines) receive a leading apostrophe for spreadsheet use. That is an export-only transformation, never a source edit. Import identifiers as text in spreadsheet software to avoid numeric precision loss.

The same-origin proxy preserves binary bytes, cookie authorization, attachment disposition and no-store headers. The UI downloads a blob only on success; limit/network errors stay inline, and 401 triggers session recovery. The file describes an imported sample, not a complete Steam corpus; the UI explicitly labels that limit.

## Issue prioritization

Issue listing accepts `category` (exact classification enum; blank means all) and `sort=severity|mentions|growth|latest`, together with `minSeverity`, page and size. Unknown category/sort values return 400. Filters apply to both rows and total; snapshot coverage still describes the whole game.

Severity sorts by score then mentions; mentions sorts by count then severity. Growth sorts by velocity descending with null baselines last, then recent mentions. Latest sorts by last-seen timestamp descending. All orders end with UUID for stable ties within a snapshot. Growth reflects the snapshot’s adjacent 7-day windows, not live wall-clock time. Filtering/sorting does not rebuild clusters or change their evidence.

## Review date ranges

Both review listing and CSV export accept optional `from` and `to` in strict `YYYY-MM-DD` format (years 0001–9999). Dates filter the Steam review creation timestamp, not its last edit or import. Bounds are inclusive calendar days in UTC: `from=2026-10-01&to=2026-10-01` selects `[2026-10-01T00:00:00Z, 2026-10-02T00:00:00Z)`. Either bound may be omitted; blank values mean no bound. Invalid dates or `from > to` return 400 `INVALID_DATE_RANGE`.

Date predicates combine with language, vote and text filters in the shared review repository and apply before counting/pagination. CSV traverses the same filtered snapshot. Review dates shown in the explorer use UTC to match the controls. These filters do not alter import scope, analysis batches or persisted data.

## Before/after comparison

`GET /api/games/{id}/comparison?date=2026-09-20&days=7` compares `[date−days,date)` with `[date,date+days)`, bounded at UTC midnight. `days` must be 7, 30 or 90. `date` is a required valid calendar date. The after period must end at or before the current instant; otherwise 400 `INCOMPLETE_PERIOD`. Dates outside the supported calendar range return 400. Workspace authorization applies as for other game routes.

Each window returns inclusive `from`/`to` dates, review/recommended counts and nullable `recommendationRate` (0–1). `reviewChangePercent` is 100×(after−before)/before, null when before has zero reviews. `recommendationChangePoints` is 100×(afterRate−beforeRate), null if either rate is missing. `imported` counts all stored reviews; `calculatedAt` timestamps the read. All counts come from one repeatable-read transaction. No data is imported, classified or persisted by this endpoint.

### Saved updates

- `GET /api/games/{id}/updates`: array ordered by release date descending, then UUID; at most 100 records per game.
- `POST /api/games/{id}/updates`: `{ "title": "Patch 1.2", "releasedOn": "2026-09-20" }` creates a manual release record.
- `POST /api/games/{id}/updates/{updateId}`: same body plus `version` edits an existing record. Returns 409 if another write changed it; 404 if it belongs to a different game or is missing.

Responses include id, title, releasedOn, version, createdAt and updatedAt. Titles are trimmed, 1–120 characters, with no control characters; dates are valid UTC calendar dates in years 0001–9999. Future/planned dates are allowed. Limit failures return 422; validation returns 400. Workspace authorization and authenticated CSRF protection apply to both writes. No automatic ingestion, analysis or comparison is triggered by saving.

Comparison `signals` includes provider/model, analyzedBefore/analyzedAfter, grouping snapshot, category/issue changes and daily counts. Change shares use successfully analyzed reviews with matching current text hash, provider/model/prompt. `changePoints` is 100 × (afterShare − beforeShare); either zero denominator yields null. Issue membership uses the existing snapshot; stale snapshots are explicitly flagged. Daily dates cover both full UTC windows, including zero-count days. NEW is first observed within the current grouping after the boundary, not proof the release introduced a bug.

`GET /api/games/{id}/usage` returns workspace-wide UTC monthly attempt counters, configured limit, known input/output token totals and attempts with unknown token usage. The endpoint inherits game/workspace authorization. Each retry reserves a slot; failed/interrupted attempts remain charged. No currency amount is inferred.

### Report snapshots

`GET /api/games/{game}/reports` lists up to 100 snapshots; `GET /api/games/{game}/reports/{id}` returns metadata and immutable payload. `POST /api/games/{game}/reports/generate` accepts `{type:"WEEKLY",requestId:"UUID"}` or `{type:"PATCH",date:"YYYY-MM-DD",days:7,requestId:"UUID"}`. Reuse the same requestId only when retrying the same creation request. Weekly means the last seven complete UTC days versus the preceding seven. Report creation uses a serializable transaction; concurrent serialization failures may be retried. Reports contain deterministic summaries, not AI claims, and source excerpts remain available if later grouping replaces issue IDs. Authorization/CSRF follow the game routes.
