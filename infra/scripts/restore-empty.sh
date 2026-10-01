#!/usr/bin/env bash
set -euo pipefail
if [[ $# -ne 3 ]]; then echo "Usage: restore-empty.sh ISOLATED_COMPOSE_FILE ENV_FILE BACKUP_FILE" >&2; exit 2; fi
compose=$1
environment=$2
backup=$3
[[ -f "$backup" ]] || { echo "Backup file not found" >&2; exit 1; }
# Refuse existing application tables. Start only postgres in the isolated target stack.
count=$(docker compose --env-file "$environment" -f "$compose" exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At' <<'SQL'
SELECT count(*) FROM information_schema.tables WHERE table_schema NOT IN ('pg_catalog','information_schema') AND table_type='BASE TABLE';
SQL
)
[[ "$count" == "0" ]] || { echo "Restore refused: target database is not empty. Use a separate empty database." >&2; exit 1; }
docker compose --env-file "$environment" -f "$compose" exec -T postgres sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --exit-on-error --single-transaction --no-owner --no-privileges' < "$backup"
echo "Restore completed. Inspect the isolated database before any cutover."
