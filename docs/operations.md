# Operations and deferred production deployment

## Current verification boundary

On 2026-10-01 the owner explicitly approved a build and restart without automated tests. Both application images built, a private database backup was created, and migrations V8–V17 applied successfully to the local V7 database. Readiness, game/review/overview/comparison/settings reads and weekly report creation/idempotent retry were checked manually. Local Stripe sandbox and Ollama setup is documented in `local-billing-ai.md`. This is not full production acceptance; a restore drill and the broader authorization/quality checks remain outstanding.

## Deployment template

`infra/production/compose.yml` is a separate, **unexecuted** deployment template. It requires APP_DOMAIN, BACKEND_IMAGE, FRONTEND_IMAGE and a strong POSTGRES_PASSWORD in an ignored environment file. Select immutable, reviewed image tags/digests after a later build. It exposes only the reverse proxy; PostgreSQL, backend and frontend ports are internal. It enables account mode and secure cookies, disables paid AI, and leaves scheduled dispatch disabled at the server level.

Choose a host/domain and configure DNS before launch. Caddy manages HTTPS when the configured domain resolves to the server and ports 80/443 are reachable; see [official proxy documentation](https://caddyserver.com/docs/quick-starts/reverse-proxy). Pin/review the Caddy and PostgreSQL image digests for the actual deployment. No domain, host, certificate, service account or image registry has been created by this change.

The deployment is single-backend-instance: sessions and authentication rate limiting remain process-local. Restarting the backend signs users out. Multi-instance public operation requires a shared session/rate-limit strategy. Membership and job/usage locks are database-backed. Production JSON logging uses the [Spring Boot 3.5 structured logging setting](https://docs.spring.io/spring-boot/3.5/reference/features/logging.html). Application request IDs are generated server-side; bodies, passwords, invite tokens and query strings are not logged by the request filter.

## Backup

Use an encrypted, access-controlled storage destination outside the repository. Example (run later on the intended environment):

```sh
infra/scripts/backup.sh infra/production/compose.yml /secure/playersignal.env /secure/playersignal-backups
```

The script uses pg_dump custom format, private file permissions and publishes the filename only after success. It does not delete older backups, upload data or schedule itself. Before launch configure daily execution on the chosen host, failure alerting, off-host encrypted copies and an explicit retention policy (initial proposal: 7 daily and 4 weekly copies, subject to owner/privacy review). The database dump contains user/player data; never commit it or send it to a public bucket.

## Restore drill

Create a **separate empty PostgreSQL 17 database/volume** and start only its postgres service. Use an isolated Compose project/configuration and matching environment file; never reuse the active application's volume. Then:

```sh
infra/scripts/restore-empty.sh /isolated/compose.yml /isolated/playersignal.env /secure/backup.dump
```

The script refuses a database with existing application tables and restores in one transaction. Verify review/report counts, workspace isolation, login, schema history and representative source text in the isolated target before a cutover. Do not attach a restored copy of the production database to an internet-facing app without controlling access. No restore drill has been executed in this implementation batch.

## Monitoring and incidents

- Poll internal readiness and external /welcome; alert on repeated failures through the provider selected later.
- Inspect workspace Operations for failed imports/analyses and correlation IDs in structured logs.
- On database/storage failure, disable scheduling, preserve volumes and inspect the failed job; rerun bounded jobs after restoring connectivity. Do not delete tables to clear errors.
- On an exposed invitation, revoke it. On a credential incident, rotate affected secrets, review membership and invalidate sessions. Backups and export files must be included in the review.
- Restore database + matching application version when migrations are not backward compatible. Do not roll back image tags blindly against a newer schema.

## External services intentionally deferred

Stripe sandbox source is installed; local webhook activation requires its CLI permission/signing secret. Live Stripe, email transport, domain provisioning and external monitoring are not active. Expiring report-share endpoints are implemented but no report has been shared or publicly deployed. No payment plans or delivery guarantees are advertised. Operator/legal review, dependency/security scans, source-terms review, production acceptance and local-model quality evaluation remain required before a commercial launch.
