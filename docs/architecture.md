# Architecture

A modular monolith on Java 21 and Spring Boot 3.5.x, with PostgreSQL 17 as the source of truth and Flyway as the only schema migration owner. Next.js renders the web UI; server-side readiness requests use `BACKEND_URL`. Database connection details remain in the backend.

Future domain packages: game, steam, review, analysis, issue, update, report, auth, billing, notification and shared. Create packages when implementing their behavior rather than empty scaffolding. Steam, AI, payment and email integrations will have mockable interfaces.

V1 creates an application schema. Flyway history stays in public. Future domain migrations should qualify tables with `playersignal`. V5 renames the historical schema; V6 introduces the workspace/account model described below.

The readiness group includes the database; liveness describes process health. Only health endpoints are exposed, without infrastructure details. The UI uses a bounded, uncached server-side request and does not infer availability from static fixture values.

UI colors follow blueprint section 23.2. The first page is a foundation status/empty state, not the complete authenticated shell. shadcn/ui primitives, Recharts, navigation and dashboard screens belong to the UI milestone. No external font requests are required.

The blueprint specifies Spring Boot 3.x, so this milestone uses 3.5.16. Reassess framework support and security updates before public deployment. JDK 21 is used in Docker and CI, regardless of the host JDK.

References checked during implementation:
- https://docs.spring.io/spring-boot/3.5/system-requirements.html
- https://nextjs.org/docs/app/getting-started/installation

## Milestone B: ingestion

- `game` owns game metadata; `review` owns evidence persistence/querying; `steam` owns external transport and audited background imports.
- The client uses the current `IUserReviewsService/GetAppReviews/v1` service: Updated order, all languages and purchase types, including off-topic activity, 100 reviews/page. Queries are encoded as `input_json`. The response is wrapped in `response`; HTTP status determines success.
- Metadata uses the observed Steam store `/api/appdetails?appids=...&l=english&filters=basic` endpoint, isolated behind `SteamClient`. This store endpoint is not a versioned, guaranteed public Web API; failures are visible and contract tests can be extended if it changes.
- HTTP connect/request timeouts, at most three attempts for network failures/429/5xx, and bounded exponential backoff. No keys required. Anonymous responses can be cached by Steam; source changes may not appear immediately.
- Two workers per process. A PostgreSQL session advisory lock keyed by App ID prevents duplicate imports across instances. Page writes use the same connection as the lock so connection loss cannot leave an unfenced worker continuing writes. The lock is explicitly released before returning its connection to the pool.
- Each page upserts reviews and advances audit counters/cursor in one transaction. Unique `(game_id, steam_recommendation_id)` and JSONB comparison prevent duplicate/no-op writes. Older source timestamps cannot overwrite a newer stored review. Source deletions are not inferred from absent results.
- Page budgets are explicit PARTIAL outcomes, never fake completion. Cursor checkpoints survive restart. Startup marks orphaned RUNNING records FAILED only after obtaining that game's lock; another active instance is left alone.
- Flyway explicitly owns history in `public`, avoiding PostgreSQL's `$user` search-path changing the default schema after the `playersignal` schema is created. V1 stays unchanged; V2 adds the three domain tables and indexes.
- V6 changes App ID uniqueness to `(workspace_id, steam_app_id)`; prior milestone B used global uniqueness.

Current integration reference: https://partner.steamgames.com/doc/webapi/IUserReviewsService (verified 2026-10-01). The previous `/appreviews` endpoint is deprecated. Live store metadata and the new service envelope were inspected; test fixtures contain synthetic review text.

## Milestone C: classification boundary

`ReviewAnalyzer` separates provider calls from orchestration. The live bean is `OpenAiReviewAnalyzer`; `FixtureReviewAnalyzer` is only used by the synthetic demo and tests, never substituted into production review processing. Live analysis defaults to disabled. The adapter sends only text/language to the Responses API with strict Structured Outputs, `store:false`, bounded output and request/connect timeouts. Errors are sanitized; response bodies and credentials are not logged.

V3 adds the `review_input` view and versioned `review_analysis` / `analysis_run` tables. SHA-256 is calculated over a JSON array of language and exact source text. Votes and playtime are excluded from the identity. Every version retains its input snapshot. Current results require matching hash/provider/model/prompt; successful rows are immutable in normal processing. The cache is restricted to one game and the same complete identity.

The analysis worker acquires a PostgreSQL session advisory lock on positive Steam App ID. Imports use negative IDs, so edits can arrive while analysis runs. Such output remains attached to its original snapshot and does not appear as the current result. Completed result and run counters commit atomically on the lock connection. Startup/start recovery marks orphaned in-flight rows failed only after acquiring the game lock. Explicit retry is required after interruption. One worker per process avoids an unbounded queue; batch size defaults to 25. Provider errors stop a batch after bounded retries, while invalid individual output can fail and allow the remaining reviews to proceed.

Not implemented: clustering, scheduled analysis, global monetary budgets, request-level billing audit, calibrated confidence, tenancy/auth, or a UI history browser. Stored token usage covers successful responses only and is not a complete cost ledger for retries. History is available through the API. Provider-live behavior remains unverified until a key is configured.

## Milestone D: local issue intelligence

V4 persists `issue_snapshot`, `issue_cluster` and `issue_mention`. Only current successful, actionable, non-POSITIVE analyses participate; outdated hashes/models/prompts, failures and skipped inputs are excluded. Every mention points to the immutable analysis input and carries similarity. The category must match exactly (a conservative compatible-category policy).

`IssueEngine` uses NFKC/case/punctuation normalization, a small fixed English stopword/inflection map and 512-dimensional hashed term-frequency vectors. These are local lexical embeddings, **not learned semantic embeddings**. Cosine threshold is 0.72; a member must match both the current centroid and the initial representative, reducing chaining. Centroids are normalized sums; the first chronological member provides the title. Input sorting and deterministic IDs make repeated rebuilds reproducible. Hash collisions and distant paraphrases remain limitations. Validate real-game cluster quality before public use; a learned embedding provider and evaluated threshold are later improvements.

Rebuilds are explicit and synchronous with a hard cap of 2000 eligible inputs, rather than silently truncating a game. PostgreSQL advisory key `Long.MIN_VALUE + steamAppId` isolates clustering from import/analysis locks. A repeatable-read transaction computes and atomically replaces the snapshot and memberships. Failures roll back the whole replacement. Reads use repeatable-read transactions to keep page, metrics and freshness metadata consistent. Fingerprints cover participating analysis IDs, source timestamps and configuration. Source changes during a rebuild appear as stale on subsequent reads. No external model calls occur in this operation.

This first implementation recomputes the bounded snapshot; incremental aggregation, semantic model embeddings, merge/split, persistent workflow status and cluster history are deferred. Existing analysis history remains intact. Data is a partial imported sample; percentages do not measure prevalence across the player population. UI provides list/detail/evidence and a separate synthetic demo; the full dashboard remains milestone E.

## Milestone E: dashboard UI

The global app shell provides desktop navigation, a mobile menu, selected-route indication and explicit live/demo context. Game routes: `/games/{id}/overview`, `/games/{id}/issues`, `/games/{id}/issues/{issue}`, `/games/{id}` (existing review URL retained), `/games/{id}/processing`. Onboarding now opens the overview. `/demo/overview` is the isolated synthetic dashboard.

The overview service computes recommendation/time-series/category data from persisted rows under repeatable read. It does not synthesize claims or infer sentiment from absent analyses. Recommendation metrics use equal-duration rolling windows; issue snapshots retain their own scope/time. Null ratios/baselines stay null through typed frontend validation. The dashboard polls only active import/analysis runs and preserves prior values with explicit errors on failed refresh.

Shared Button/Card sources are adapted from the official shadcn/ui registry with blueprint tokens. Attribution is in `frontend/THIRD_PARTY_NOTICES.md`. Recharts renders recommendation volume/rate with fixed zero-based scales, no animation, keyboard accessibility and an exact-value table. Category bars expose exact counts and denominators as text. Responsive shell uses a 224 px desktop sidebar, content max-width 1440 px and stacked mobile cards; reduced motion disables transitions.

Frontend component tests exercise empty/demo/error/retry/sync controls and navigation state with Testing Library. They complement HTTP integration tests and browser layout checks; they do not substitute for complete browser click-through acceptance.

Implementation references: https://ui.shadcn.com/docs/installation/manual and https://github.com/recharts/recharts/wiki/Recharts-and-accessibility (checked 2026-10-01).

## Milestone F1: account boundary

Spring Security uses explicitly saved server-side sessions and BCrypt cost 12. Registration inserts account and workspace transactionally; normalized email uniqueness prevents duplicate accounts. Authentication rotates sessions. Session-backed CSRF is required on mutations when enabled. JSON APIs return errors rather than redirecting to framework login forms.

WorkspaceContext selects the authenticated workspace, or the fixed legacy workspace when authentication is disabled. Game listing/upsert includes workspace ownership. A filter checks decoded application paths for every game and nested resource before controller execution; inaccessible games return 404. Background jobs receive the already authorized game ID and do not depend on request-thread security context. Existing per-App-ID locks conservatively serialize jobs even across workspaces.

V1–V4 checksums remain unchanged. V5 renames the schema; V6 assigns existing games to the local workspace without assigning that workspace to a signup. This is an opt-in foundation, not completion of public beta. Sessions/rate limits are process-local; public deployment still needs recovery/verification flows, proxy-aware distributed throttling, HTTPS configuration and operational review.

References: [Spring Security CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html), [password storage](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html), [context persistence](https://docs.spring.io/spring-security/reference/servlet/authentication/persistence.html).

## Milestone F2: workspace access and session recovery

Workspace pages mount their data-fetching children only after a successful session check. Anonymous users see sign-in/registration links; outages show a retryable access-check error. Public demo and account routes remain outside this UI boundary. Game API 401 responses unmount workspace content and clear the account header; returning to a tab rechecks the session before reloading workspace data. Abort guards prevent older requests from restoring stale access state. This UI boundary complements, and does not replace, backend ownership checks.

Login/registration preserve the requested workspace path and query filters via `next`. A shared allowlist limits return destinations to the library and known game pages, rejecting external origins, backslashes, control characters and unrelated routes. Successful authentication performs a full navigation to discard cached account data. Registration into a new workspace does not grant access to the previous account's game IDs; the backend still returns 404 for those IDs.
