# PlayerSignal

Evidence-first Steam review intelligence. Based on the supplied original Product & Engineering Blueprint v1.1. PlayerSignal is the product and repository name.

## Current scope

Milestones A–E: Spring Boot modular monolith, Next.js frontend, PostgreSQL/Flyway, Steam game lookup, audited background review imports and a review explorer with versioned AI classification. Issue grouping and evidence drill-down are included; the MVP dashboard is included. An opt-in account/workspace foundation is included; billing remains a later milestone. This is a local alpha, not a public deployment.

## Run the complete stack

Requires Docker Desktop (running) and Docker Compose v2. From the repository root:

```sh
cp .env.example .env
docker compose --env-file .env -f infra/docker-compose.yml up --build -d --wait
```

If port 5432 is occupied, set `POSTGRES_PORT=5433` in `.env` (this checkout already uses 5433). For native backend development with this override, also set `DATABASE_URL` to port 5433 and the database name from your existing `.env`.

Open http://localhost:3000. Backend health: http://localhost:8080/actuator/health. Readiness (including PostgreSQL): http://localhost:8080/actuator/health/readiness. Ports bind to loopback only. The example password is exclusively for local development; replace it before any shared deployment.

```sh
docker compose --env-file .env -f infra/docker-compose.yml logs -f
docker compose --env-file .env -f infra/docker-compose.yml down
```

The database volume survives `down`. Do not add `-v` unless you intend to delete the database. PostgreSQL credentials initialize a fresh volume; changing `.env` does not change an existing database password.

## Develop locally

Use JDK 21, Maven 3.9+, Node.js 22 and Docker. Start only PostgreSQL:

```sh
docker compose --env-file .env -f infra/docker-compose.yml up -d postgres
```

In a backend terminal (default database name/port from `.env.example`):

```sh
cd backend
POSTGRES_PASSWORD=local-development-only mvn spring-boot:run
```

For custom database settings pass `DATABASE_URL`, `POSTGRES_USER`, `POSTGRES_PASSWORD`; `.env` is loaded by Compose, not automatically by Maven. In another terminal:

```sh
cd frontend
npm ci
npm run dev
```

The frontend reads `BACKEND_URL` server-side (default `http://localhost:8080`). No browser-side secret or permissive CORS is needed.

## Verification

```sh
cd backend
mvn -B verify
```

Tests require Docker and use isolated Testcontainers PostgreSQL, proving HTTP health/readiness and the successful Flyway migration. They do not silently skip when Docker is unavailable.

```sh
cd frontend
npm ci
npm run lint
npm run typecheck
npm test
npm run build
```

CI runs these checks with Java 21 / Node 22 and builds both Docker images. Docker image builds skip Java tests because verification runs separately in CI.

## Structure

- `backend/`: Spring Boot application; domain packages added alongside features.
- `frontend/`: Next.js App Router, strict TypeScript and Tailwind 4; blueprint color tokens.
- `infra/`: local Compose stack with persistent PostgreSQL.
- `docs/architecture.md`: boundaries and decisions.
- `docs/progress.md`: staged implementation and validation record.

V1 creates the historical application schema; V5 renames it to `playersignal` without rewriting migration history; V6 adds accounts and workspaces; V2 adds games, reviews and ingestion runs; V3 adds analysis runs and versioned results; V4 adds issue snapshots, clusters and evidence memberships. No invented customer or review rows are seeded.

## Connect and import a game

Open the home page, enter a Steam App ID or full HTTPS store URL, then click **Connect game**. Verify the game name and click **Sync reviews**. The explorer shows saved import progress and supports text, recommendation, language, UTC date, current category/status and issue filters, with consistent pagination and CSV export.

Each run imports at most `STEAM_MAX_PAGES` pages (default 10 × 100 reviews). **Resume import** continues a PARTIAL or FAILED run from its last saved page. A fresh scan after COMPLETED checks for new and edited reviews. `STEAM_PAGE_DELAY_MS` defaults to 500; retries are bounded. Only two imports run at once. Limits are environment-configurable in Compose.

Run status remains visible on failure; existing reviews are retained. Raw source JSON is stored for traceability, but not returned by the browser API. OpenAPI is served at http://localhost:8080/openapi.json.

## Classify reviews (milestone C)

The separate `/demo` page contains synthetic examples and never writes to the database. Live imported reviews use conservative English phrase rules by default (`ANALYSIS_PROVIDER=local`); those rules are not an AI model and leave unmatched text explicitly unclassified. Keep `ALLOW_PAID_AI=false`. No paid provider is needed for the local workflow.

Choose **Analyze next batch** in Processing after running an updated application image. Default batch size is 25, configurable from 1–100. Optional Ollama classification and semantic grouping use an independently installed local model; see the local-mode sections below. The OpenAI adapter is dormant unless its provider, model, key and paid-call opt-in are all deliberately configured. It was not called during this work.

Analysis is manual by default. Optional per-game automation imports, analyzes one local batch and groups issues. Durable usage quotas count attempts, including retries; cache hits and skipped reviews do not consume attempts.

Results include sentiment, category, severity, confidence, actionable/likely-bug flags, issue summary, exact source quotes and tags. Expand a review's analysis to inspect evidence and the actual response model. Confidence is a model estimate, not a calibrated probability. Empty/generic/oversized input is skipped with a reason. Failed items need **Retry failed + pending**; provider configuration failures stop the batch.

The cache identity includes text + language hash, provider, requested model and prompt version. Identical input within one game can reuse a result. Text/language changes create a new version; helpful-vote changes do not. Older source snapshots remain accessible through the history API. Changing the prompt requires bumping `PROMPT_VERSION`.

The adapter uses the [OpenAI Responses API with Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs), `store:false`, strict JSON schema and server-side validation. Refusals, incomplete output, invalid types and quotes absent from the original text are rejected. No live provider call has been verified in this checkout because no API key is configured; contract tests use a local HTTP server.

## Group issues (milestone D)

Open **Issues & evidence** from a game, then **Rebuild issues**. The local algorithm groups current actionable analyses using normalized lexical vectors, cosine similarity and matching categories. The default lexical mode makes no additional model calls; optional local semantic grouping is described below. With no successful analyses, the page stays empty and links to `/demo/issues`, a separate six-review synthetic corpus.

Issue detail preserves original input text, extracted quotes, source/analysis IDs, model/prompt provenance and similarity. Metrics show mentions, first/last seen, mean severity, negative classification ratio, confidence and two adjacent 7-day mention windows. A missing prior-week baseline is shown explicitly. Results carry a calculation time and become visibly stale when participating analyses change; rebuild to refresh.

The first version rebuilds at most 2000 actionable inputs per game atomically. It preserves the previous snapshot on failure and rejects overlapping rebuilds. Local lexical matching is conservative and may split paraphrases; real-game semantic quality has not yet been evaluated. Detailed formulas and API contracts are in `docs/api.md`. Rebuilds can replace issue IDs when their representative changes. Opt-in scheduling and manual issue status are available; merge/split remains outside this alpha.

## Dashboard (milestone E)

Open a game from the library to view its overview, or visit `/demo/overview` for a clearly marked read-only synthetic dashboard. The sidebar connects overview, issues, reviews and processing; on mobile use the navigation menu.

Overview supports 7/30/90-day rolling windows, Steam recommendation rate, volume comparisons, accessible daily charts, current classification categories and linked issue evidence. Coverage and issue snapshot metrics retain their own scope and calculation time. A partial import remains explicitly partial. Empty AI results do not produce invented category counts or findings.

Search original review text on the Reviews page. Filter Issues by minimum severity and open a title for source evidence. Processing exposes the existing real sync/analysis actions and error states. Live AI still requires backend configuration described above. Public beta hardening and billing remain pending; date comparisons and report exports are available as described below.

## Accounts and workspaces (milestone F, first slice)

Accounts are opt-in. The current installation keeps `AUTH_ENABLED=false`, preserving direct access to the existing local workspace and imported reviews. Set `AUTH_ENABLED=true` in `.env`, recreate the backend with the Compose command above, then open `/register`. Registration creates a new private workspace; `/login` signs in and the header offers sign-out. Passwords require at least 12 characters and at most 72 UTF-8 bytes. Cookies are HttpOnly, SameSite=Lax, with a 30-minute idle session timeout. Use `AUTH_COOKIE_SECURE=true` when serving over HTTPS.

Existing local data is not assigned to the first person registering. A deliberate operator-controlled data migration is required to move it into an account; no transfer UI is included yet. Disabling accounts exposes only the original local workspace, never private account workspaces.

Sessions and login throttling are held in one backend process. Restarting it signs users out. Throttling is 8 attempts per normalized email and 30 per remote IP per 5 minutes; the Next.js proxy shares its backend IP. Distributed sessions/rate limits, email verification, password recovery, email invitation delivery and a full deployment review remain before public beta.

The product, UI, packages, Java namespace, application schema and Compose project are now PlayerSignal. Historical Flyway V1–V4 remain immutable. This upgraded installation retains its existing database login/name and external volume identifiers in ignored `.env` so data survives the rename; new installations use PlayerSignal defaults. Never overwrite an existing `.env` with the example during upgrade.

Workspace pages now check access before loading their data. With accounts enabled, an expired session shows a sign-in screen and preserves the selected game page and filters through login/registration. Returning to a tab rechecks the session. Demo pages stay public; backend failures show an access-check retry instead of treating an outage as a logout.

## Export review evidence

On the Reviews page, apply language, recommendation or text filters, then choose **Export filtered CSV**. The export includes all matching imported reviews across pages, up to 5000 rows / 10 MiB. Larger results require narrower filters and are never silently truncated. Files contain source text and review metadata, with UTC timestamps and formula-prefix protection for spreadsheets. Treat the data as an imported sample; this is the first report feature, not a generated analytical report. Import Steam review IDs as text to preserve their precision.

The Issues page supports category and minimum-severity filters, plus sorting by severity, mention count, growth or last seen. Settings are URL-addressable and preserved while paging. Growth uses the saved snapshot’s comparison windows; missing baselines sort last rather than being treated as zero growth.

Reviews and CSV export also support **From (UTC)** and **To (UTC, inclusive)**. The end date includes the whole day; either boundary may be left empty. Dates refer to when the Steam review was created. Applying or clearing filters starts on the first page, while pagination preserves the selected range.

## Inspect analysis history

Each review offers **View analysis history**. Expand it to load saved versions, identify the version used by the current review view, inspect model/prompt/provider metadata and reopen the exact input text and its SHA-256 hash. Failed/skipped versions keep their status and reason. Closing and reopening refreshes history; opening history never starts an analysis or provider call. A review without saved versions shows an explicit empty state. Retries update their existing version rather than producing an attempt-by-attempt audit log.

## Before & after

Choose **Before & after** in a game's navigation. Enter a reference date (for example, an update date) and 7, 30 or 90 days on each side. The selected day begins the after period; both windows must be complete UTC days. The view compares imported review volume and Steam recommendation rates and links to the exact reviews in each period. Missing baselines stay explicit. Results describe a partial imported sample and do not establish that an update caused a change. Saved release dates are available under **Updates**; patch-note analysis remains pending.

The **Download report (.md)** button in Before & after saves the displayed comparison as a Markdown report, including UTC windows, calculation time, counts, recommendation rates, changes and source links. It runs locally in the browser without AI calls. Source links require access to this workspace; localhost links are usable only on the host machine. Subsequent imports may change the linked review lists.

### Saved updates

Open **Updates** in a game to save a release name and UTC date, edit it, or open a 7/30/90-day comparison. Planned dates are allowed, but comparisons require complete periods. Up to 100 updates per game are supported. Concurrent edits are rejected with a reload prompt. These are manually entered records; Steam patch-note ingestion, deletion and automatic patch analysis are not implemented.

### No paid AI mode

New configuration defaults to `ANALYSIS_PROVIDER=local`: conservative English phrase rules applied to real review text, with exact evidence and low, uncalibrated confidence. This is **not AI**. Non-English inputs are skipped; unmatched English text is unclassified, not a reliable sentiment prediction. Existing `.env` values override defaults.

Optional local model: install/configure Ollama separately, use an installed non-cloud model with `ANALYSIS_PROVIDER=ollama`, `ANALYSIS_MODEL=<model>` and `OLLAMA_URL=http://host.docker.internal:11434` for Docker Desktop (native backend: `http://127.0.0.1:11434`). No model is automatically downloaded. Use an Ollama installation with cloud features disabled and a locally installed model; the local URL alone cannot guarantee how an independently configured server executes a model. PlayerSignal does not select a cloud fallback when the local service fails. Local model quality and hardware requirements must be evaluated before relying on results. The adapter follows [Ollama chat](https://docs.ollama.com/api/chat) and [structured output](https://docs.ollama.com/capabilities/structured-outputs) contracts.

OpenAI is blocked unless **both** `ANALYSIS_PROVIDER=openai` and `ALLOW_PAID_AI=true` are explicitly configured, with a server key and model. Leave `ALLOW_PAID_AI=false` for this project. Changing providers creates a new analysis identity; prior evidence/history is preserved and clusters require rebuilding.

### Optional local semantic grouping

Use `EMBEDDING_PROVIDER=ollama`, `EMBEDDING_MODEL=<installed embedding model>` and a local `OLLAMA_URL`. Keep `OLLAMA_NO_CLOUD=1` on the Ollama server. The optional `infra/local-models.compose.yml` overlay configures that flag and private service access; it does not install or download a model. The default remains lexical, without model calls. No Ollama executable was found on PATH during this implementation.

The adapter follows [Ollama's embedding API](https://docs.ollama.com/api/embed), validates finite normalized vectors and equal dimensions, caches by analysis/input/model/revision and never falls back to a hosted service. Rebuilds preserve old grouping on failure. Threshold 0.78 is an unevaluated starting point, not a quality guarantee. Pin the installed model and change `EMBEDDING_REVISION` when its weights change. Cached vectors are deleted with their source analyses. One request has a 35-second deadline and 32 MiB response cap; larger/cold models can fail visibly. Cloud disabling follows the [official local-only configuration](https://docs.ollama.com/faq#how-do-i-disable-ollama-cloud-features).

### Reports, alerts and retention

Reports supports private weekly snapshots; Before & after can save patch snapshots. Summaries are deterministic evidence briefs, not generated AI assessments. Owners can explicitly create a 1/7/30-day read-only link, replace it or revoke it. Links reveal only the selected game name, period, brief and preserved source excerpts. They do not grant workspace access. Copies already downloaded cannot be recalled; localhost URLs are not remotely accessible until hosting is configured.

Processing contains opt-in high-severity growth alerts and owner-only retention settings. Alert evaluation uses complete UTC weeks, minimum coverage and a non-stale grouping; no email is sent. Local rules assign only medium severity, so high-severity alerts generally require model classifications reviewed by a human. Retention requires a preview and explicit confirmation before enabling daily deletion. Old reviews and reports use separate periods; saved reports can preserve old source quotes. Purging reviews clears groups for a later rebuild. A later Steam import may reintroduce old reviews. Both features remain inactive by default.

Current source includes migrations through V16. The running local database inspected on 2026-10-01 was still at V7. No application builds, migrations or automated tests were run for the latest batch. See `docs/delivery-plan.md` and `docs/quality-review.md` before treating these changes as release-ready.
