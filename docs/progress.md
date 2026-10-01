# Implementation progress

Source: original Product & Engineering Blueprint v1.1 (35 pages), supplied by the project owner. The original PDF remains in Downloads. Its embedded example prompts are reference material, not separate user requests.

## Milestone A — foundation

Implemented:
- Java 21 / Spring Boot 3.5.16 application and health/readiness endpoints.
- PostgreSQL 17, persistent Compose volume and loopback-only published ports.
- Flyway V1 creates the historical application schema (renamed by V5); no domain tables or customer fixtures yet.
- Next.js 16.3.8, React, strict TypeScript, Tailwind 4 and blueprint color tokens.
- Initial empty state, streaming connection check, retry action and route error boundary.
- Multi-stage Docker builds with non-root runtime users.
- Environment example, setup/API/architecture documentation and GitHub Actions checks.

Verification on 2026-10-01:
- `mvn -B verify`: passed, 2 PostgreSQL Testcontainers integration tests; no skipped tests. Host JDK 25 compiles for release 21.
- `npm run lint`: passed.
- `npm run typecheck`: passed.
- `npm test`: passed, 5 health-boundary tests.
- Dependency install audit after Vitest upgrade: zero known vulnerabilities.
- Native `npm run build`: blocked by a Turbopack subprocess port-binding permission error, including the escalated attempt. Docker is the reproducible build environment for this machine.
- Docker frontend production build: passed on Node 22.
- Docker backend production build: passed on Java 21.
- Compose stack: running; backend and PostgreSQL healthy. Browser confirms the frontend displays live connected status.
- Local `.env` uses PostgreSQL port 5433 because 5432 is already occupied; example configuration retains the standard 5432.

Scope limits: no game connection, review ingestion, AI, full dashboard shell, shadcn/ui components, auth or billing yet. UI accessibility and mobile layout need full browser acceptance testing with the actual dashboard milestone. CI configuration exists but has not been run on GitHub; no remote repository or commit was created.

## Milestone B — Steam ingestion (implemented)

- V2 adds `game`, `review`, `ingestion_run`, uniqueness constraints and pagination/audit indexes.
- Steam App ID / HTTPS store URL parsing, metadata lookup and idempotent game upsert.
- Current Steam reviews service behind a mockable `SteamClient`; bounded retries and timeouts.
- Background imports with PostgreSQL per-game locks, atomic page writes/checkpoints and resume after partial runs or failure.
- Explicit RUNNING / COMPLETED / PARTIAL / FAILED states, counters, timestamps and diagnostic errors.
- REST endpoints and served OpenAPI at `/openapi.json`.
- Functional onboarding and review explorer with language/recommendation filters, URL pagination, loading/empty/error states and real progress.
- Fixed foundation restart issue: Flyway history schema is explicitly `public`; added regression test with database username matching the app schema.

Verification on 2026-10-01:
- `mvn -B -ntp verify`: 25 tests passed, including PostgreSQL/Testcontainers, idempotence, edits, failure resume, concurrent conflict, import cap, restart recovery, HTTP contract and URL validation.
- `npm run lint`, `npm run typecheck`: passed.
- `npm test`: 15 tests passed, including API boundary validation and same-origin proxy behavior.
- `docker compose --env-file .env -f infra/docker-compose.yml up --build -d --wait`: both production builds passed; all services running.
- Live smoke via the same-origin frontend HTTP route: connected Portal 2 (App 620), imported 1000 real reviews in 10 pages, correctly finished PARTIAL. Review filtering returned 12 English negative reviews; page 2 contained different records from page 1.
- Browser rendering confirmed real counts, source text, partial status and matching filtered counts. At a 390 px viewport, document width remained 390 px. Automated browser clicks did not reliably dispatch in this environment; full click-through acceptance remains unverified. HTTP mutations and browser rendering were checked separately.
- OpenAPI served successfully with all 5 resource paths.

Limits: the dataset is explicitly partial; each click resumes at most another 10 pages by default. No source-deletion reconciliation, automatic scheduling, workspace isolation, authentication, classification or clustering. Interrupted imports require an explicit resume. Steam may cache anonymous responses. Full UI polish is still milestone E. No GitHub CI run, remote repository or commit was created.

## Milestone C — AI classification (implemented, live provider unverified)

- V3 adds versioned analysis results, immutable source snapshots, text/language SHA-256 and run counters.
- Provider interface, OpenAI Responses adapter with strict Structured Outputs, exact evidence checks, refusal handling and bounded retries.
- Manual batches, one worker, per-game database lock, failed-item retry, interruption recovery and same-game result cache.
- Current-version results in the review API/UI, status counts, expandable evidence, model/prompt provenance and API history.
- Isolated synthetic `/demo`, clearly labeled as deterministic rules, with no provider calls or persistence.
- Server-only opt-in configuration and updated OpenAPI/API documentation.

Verification on 2026-10-01:
- Backend: 37 tests passed, including PostgreSQL migrations, caching, source changes in flight, explicit retries, concurrent conflicts, batch caps, orphan recovery, cross-game history protection and local HTTP provider-contract tests.
- Frontend: lint, TypeScript and all 20 tests passed, including classification parsing and synthetic/live separation.
- Docker production builds passed and the complete stack is healthy.
- HTTP smoke: 1000 existing reviews remain pending, disabled-provider POST returns 503, and the synthetic demo returns four examples. Database checks confirm zero analysis results/runs were created.
- Browser confirmed current counts, disabled live action, pending review labels and all four demo outputs; screenshot at `/tmp/playersignal-milestone-c.png`. Automated link clicks remain unreliable in this environment, so full click-through acceptance is unverified.
- Live OpenAI calls have not been tested: no API key is configured. Real imported reviews are not given fixture results.

## Milestone D — issue intelligence

- V4 persists clusters, analysis memberships, centroids and snapshot metadata.
- Deterministic local lexical embeddings + cosine threshold + exact category compatibility, stable input ordering and anti-chaining check.
- Explicit atomic rebuild, 2000-input cap, per-game locking, stale-input detection and preservation of previous results on failure.
- Mention count, dates, negative ratio, mean severity/confidence and adjacent-week velocity with an explicit missing-baseline state.
- Paginated list/detail APIs and basic UI, exact source snapshots, model/prompt provenance and separate six-review/three-issue demo.
- Bounded full recomputation is used in this version; incremental aggregation and learned semantic embeddings are deferred. Do not treat the local lexical vectors as semantic embeddings or claim real-game clustering quality has been validated.

Verification on 2026-10-01:
- Backend: all 45 tests passed. New cases cover fixed-corpus grouping, order stability, category boundaries, zero-baseline velocity, idempotence, evidence pagination, wrong-game access, source/model changes, concurrent rebuild exclusion and atomic rollback under forced storage failure.
- Frontend: lint/typecheck and all 24 tests passed, including metric validation and synthetic-mode boundaries.
- Docker: production builds passed; PostgreSQL/backend/frontend healthy.
- Live HTTP: empty Portal 2 issue list, successful empty rebuild with fresh metadata, and synthetic endpoint returning three groups with two evidence records each.
- Browser: verified live empty state and all three demo groups/metrics. Preview saved at `/tmp/playersignal-milestone-d.png`. Automated disclosure interactions remain unreliable; full click-through acceptance is unverified.
- No live classification or real-game clustering-quality evaluation: imported reviews still await an OpenAI key. Learned semantic embeddings and incremental recomputation remain deferred as documented.

## Milestone E — MVP dashboard

Implemented:
- Shared responsive shell with 224 px desktop sidebar, mobile navigation, route selection and explicit demo/live context.
- Overview API and dashboard: 7/30/90-day windows, recommendation ratio and volume changes, UTC daily counts, category distribution, issue links and processing coverage.
- Shared shadcn/ui Button/Card sources with attribution; Recharts volume/rate chart, exact-value table and reduced-motion support.
- Loading skeletons, empty/classification-pending states, partial-import notice, stale snapshot indicators, inline errors and retry/sync controls.
- Issue severity filtering and table, literal review-text search, URL filters/pagination and dedicated processing route. Existing review URLs retained.
- Separate fixed ten-review demo dashboard; no database fixtures or provider calls in the live path.

Verification on 2026-10-01:
- Backend: 49 tests passed, including window boundaries, zero-filled dates, null baselines, future-date exclusion, demo isolation, search validation and severity filtering.
- Frontend: lint/typecheck and all 30 tests passed, including component interactions for sync/running/error/retry/demo/navigation. JSDOM reports a harmless unsupported full-document navigation after the mocked navigation test; real browser navigation was checked separately.
- Docker production build and complete stack passed. Dependency install audit reported zero known vulnerabilities.
- Live HTTP overview: Portal 2 retains 1000 imported reviews; 824 in the rolling 30-day window, 807 recommended (97.9%), zero classified categories. Counts sum correctly from daily buckets.
- Browser: a stalled original tab was replaced for verification. Real clicks confirmed range changes, chart metric switching, issue navigation and evidence disclosure. Mobile menu opened/closed and review search for `puzzles` returned 44 real matches.
- Responsive checks: no page overflow at 1440, 1280 and 390 px; desktop and mobile states rendered. Temporary viewport overrides were reset. Preview: `/tmp/playersignal-milestone-e.png`.
- No browser console errors observed in the verification tab.

Remaining scope/limits: live AI and cluster quality across representative games still require configuration/evaluation; no public auth/tenancy. Advanced review category/issue/date filters, additional issue sorts, per-issue trend charts and automated complete onboarding acceptance remain follow-up UI refinements. The implemented dashboard is a local MVP, not a production-readiness claim.

## Next: validate MVP, then milestone F — public beta

Configure and evaluate live classification on representative games before public release. Public beta adds workspace authorization, onboarding hardening, patch comparison, reports and deployment.

Later: complete F public beta; G paid SaaS.

## Rename and milestone F1 — accounts/workspaces

- PlayerSignal branding, Java/package/config namespaces, application schema and Compose project. Immutable V1–V4 migrations and original source PDF retained; legacy database/volume identifiers preserved for compatibility.
- Opt-in registration/login/logout, BCrypt cost 12, rotated HttpOnly session cookies, CSRF, bounded in-memory authentication throttling.
- New workspaces per account, workspace-scoped game uniqueness/listing and ownership checks across all nested game resources. Legacy local data is never automatically claimed by registration.
- Account forms/header state, same-origin cookie/CSRF proxy and account-aware API writes.
- Backend: 55 tests passed, including session rotation/logout, CSRF, cross-workspace requests, encoded paths and duplicate-account transaction rollback. Frontend: 37 tests passed; lint/typecheck passed. Both production Docker builds passed.
- Accounts remain disabled in the existing local installation. This is the first slice of F; public beta and live AI evaluation remain incomplete.
- Live upgrade: all six Flyway migrations succeeded; the existing Portal 2 game and all 1000 reviews remain in the local workspace, with zero accounts created. HTTP session reports local mode; browser confirms PlayerSignal branding, account settings and the retained dashboard. Preview: `/tmp/playersignal-rename.png`.

## Milestone F2 — workspace access and session recovery

- Protected library/game pages check the session before mounting data views. Anonymous access offers login/registration with a validated return path; session-check failures allow retry without exposing cached content.
- Game API 401 responses remove private content. Tab focus/visibility changes recheck access and account-header state; stale checks are aborted. Local mode and public synthetic demos remain available.
- Login and registration retain the workspace destination and query filters. Only known same-origin workspace routes are allowed after authentication.
- Verification: frontend lint/typecheck and 60 tests passed; production Docker build passed. Browser checks in an isolated temporary auth-enabled stack confirmed the access screen, login/register links retaining the destination, and public demo access. Existing local Portal 2 dashboard still shows 1000 reviews. Preview: `/tmp/playersignal-f2-access.png`.
- No new migration, live account, provider call or auth-setting change in the main installation. Browser checks did not submit credentials; authenticated-state/expiry flows are covered by component tests and prior backend auth tests. Public beta recovery/verification and deployment work remain pending.

## Milestone F3 — filtered review CSV export

- New workspace-authorized `GET /api/games/{id}/reviews/export` and **Export filtered CSV** action. Language/recommendation/literal text filters match the explorer; all pages use one repeatable-read snapshot and stable newest-first ordering.
- CSV includes source review IDs/text, language, votes, playtime and UTC timestamps. UTF-8 BOM, escaped quotes/newlines and spreadsheet formula-prefix neutralization; source records are unchanged. No author IDs, raw payloads or invented AI results.
- Explicit 5000-row / 10 MiB limits return 422 before sending a file. UI explains partial sample coverage, keeps errors visible and routes expired sessions through the existing access guard. Proxy forwards binary bytes and safe attachment/no-store headers.
- Verification: all 60 backend tests and 64 frontend tests passed; lint/typecheck and both Docker production builds passed. Integration cases cover filters, pagination, empty files, row/byte limits, escaping and cross-workspace/anonymous export denial.
- Live verification: full export parsed to 1000 unique reviews; English/not-recommended export contains 12 rows, matching API IDs and source text exactly after documented formula protection. Browser button produced a 3096-byte file in Downloads identical to the HTTP export. Browser automation's download-event wait timed out even though the file was saved; filesystem verification confirmed the download. Preview: `/tmp/playersignal-f3-export.png`.
- Scope: this is source-evidence export, not a generated analytical report. Import Steam review IDs as text in spreadsheets to avoid numeric precision loss. No schema migration, live AI call or authentication configuration change.

## Issue prioritization — dashboard refinement

- Added exact-category filtering combined with the existing minimum-severity filter. Four descending sort orders: severity, mentions, growth and last seen; default severity behavior preserved.
- Stable UUID tie breakers; null growth baselines sort last, including after negative growth. Fixed allowlisted SQL order expressions and enum validation reject unknown parameters with 400.
- URL-addressable controls and pagination preserve filters/sort. UI explains snapshot-based growth and missing baselines; no regrouping or source mutation occurs.
- Verification: 62 backend tests and 65 frontend tests passed; lint/typecheck and both Docker builds passed. Tests cover all orderings, ties across pages, combined filter counts, invalid parameters and frontend query/pagination state.
- This completes an outstanding dashboard refinement; full public beta, patch comparisons and generated reports remain pending. Live issue lists remain empty until real analyses are configured; no fixtures are added to customer data.
- Browser verification: selected BUG and Fastest growth, submitted the form with Enter, and confirmed the resulting URL and retained selections. The existing Portal 2 empty state remains accurate (no live AI analysis). Preview: `/tmp/playersignal-prioritization.png`.

## Review exploration — UTC date ranges

- Added optional From/To calendar dates to review browsing and CSV export. Dates refer to Steam creation time; both ends include the selected UTC day. Either bound can be omitted.
- Shared backend validation rejects impossible/reversed dates with 400. Timestamp predicates use explicit UTC offsets, apply before count/pagination and remain consistent across export pages. No schema/data changes.
- UI preserves the range in URL pagination/export, resets pagination when applying filters and renders review dates in UTC. Existing language/vote/text filters combine with the range.
- Verification: 66 backend tests and 66 frontend tests passed; lint/typecheck and Docker builds passed. Cases cover leap days, open bounds, invalid inputs, midnight boundaries, a 103-row filtered export spanning pages, and frontend query/date/pagination propagation. Test fixture timestamps explicitly specify UTC so expectations do not depend on the host timezone.
- Live check: English, not-recommended reviews created 2026-09-26 through 2026-09-29 UTC produced 3 rows; CSV IDs/count and timestamps matched the list. Browser form submission retained both dates and showed the same 3 matching reviews. Screenshot: `/tmp/playersignal-date-filters.png`. Original 1000-review dataset and local auth mode remain unchanged.
