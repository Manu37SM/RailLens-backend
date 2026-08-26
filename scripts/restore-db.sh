#!/usr/bin/env bash
set -euo pipefail

DUMP_FILE="${1:-}"
TARGET_URL="${2:-${DATABASE_URL:-}}"

if [ -z "$DUMP_FILE" ] || [ -z "$TARGET_URL" ]; then
  echo "Usage: $0 <dump-file.sql.gz> <target connection string>" >&2
  exit 1
fi

if [ ! -f "$DUMP_FILE" ]; then
  echo "No such file: $DUMP_FILE" >&2
  exit 1
fi

if ! command -v psql >/dev/null 2>&1; then
  echo "psql not found - install the PostgreSQL client tools first." >&2
  exit 1
fi

echo "Restoring $DUMP_FILE into the target database..."
echo "This runs --clean/--if-exists DROP statements from the dump against the TARGET before recreating everything - make sure TARGET_URL really is the database you intend to overwrite."
read -r -p "Type the target host to confirm: " CONFIRM_HOST
EXPECTED_HOST="$(echo "$TARGET_URL" | sed -E 's#^[a-zA-Z]+://[^@]*@##; s#[/?].*##')"
if [ "$CONFIRM_HOST" != "$EXPECTED_HOST" ]; then
  echo "Host confirmation didn't match ($EXPECTED_HOST) - aborting, nothing was touched." >&2
  exit 1
fi

gunzip -c "$DUMP_FILE" | psql "$TARGET_URL"

echo "Restore complete."
