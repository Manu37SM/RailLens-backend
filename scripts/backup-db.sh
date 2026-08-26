#!/usr/bin/env bash
set -euo pipefail

DB_URL="${1:-${DATABASE_URL:-}}"

if [ -z "$DB_URL" ]; then
  echo "Usage: DATABASE_URL=<connection string> $0" >&2
  echo "   or: $0 <connection string>" >&2
  exit 1
fi

if ! command -v pg_dump >/dev/null 2>&1; then
  echo "pg_dump not found - install the PostgreSQL client tools (e.g. 'apt install postgresql-client' / 'brew install libpq') first." >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKUP_DIR="${BACKUP_DIR:-$SCRIPT_DIR/../../backups}"
mkdir -p "$BACKUP_DIR"

TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT_FILE="$BACKUP_DIR/raillens_${TIMESTAMP}.sql.gz"

echo "Backing up to $OUT_FILE ..."

pg_dump --no-owner --no-privileges --clean --if-exists "$DB_URL" | gzip > "$OUT_FILE"

echo "Done: $OUT_FILE ($(du -h "$OUT_FILE" | cut -f1))"

KEEP=14
ls -1t "$BACKUP_DIR"/raillens_*.sql.gz 2>/dev/null | tail -n +$((KEEP + 1)) | xargs -r rm -f
