#!/usr/bin/env bash
set -euo pipefail
umask 077
if [[ $# -ne 3 ]]; then echo "Usage: backup.sh COMPOSE_FILE ENV_FILE BACKUP_DIRECTORY" >&2; exit 2; fi
compose=$1
environment=$2
directory=$3
mkdir -p "$directory"
partial=$(mktemp "$directory/.partial.XXXXXX")
trap 'rm -f "$partial"' EXIT
docker compose --env-file "$environment" -f "$compose" exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom --no-owner --no-privileges' > "$partial"
[[ -s "$partial" ]] || { echo "Empty backup refused" >&2; exit 1; }
final="$directory/playersignal-$(date -u +%Y%m%dT%H%M%SZ)-$(basename "$partial").dump"
mv "$partial" "$final"
printf '%s\n' "$final"
