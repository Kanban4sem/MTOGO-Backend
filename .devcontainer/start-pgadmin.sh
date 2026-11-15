#!/usr/bin/env bash
set -eu

cd "$(dirname "$0")/.."

if [ ! -f .env ] || ! grep -q '^PGADMIN_PASSWORD=.' .env; then
  echo "pgAdmin skipped: set PGADMIN_PASSWORD in .env, then run bash .devcontainer/start-pgadmin.sh."
  exit 0
fi

docker compose --env-file .env -f .devcontainer/compose.yml up -d
