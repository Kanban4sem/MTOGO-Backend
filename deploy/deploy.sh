#!/usr/bin/env bash
# Run only through the deploy key, as "<environment> <service> <tag>"
set -euo pipefail

read -r ENV_NAME SERVICE TAG EXTRA <<< "${SSH_ORIGINAL_COMMAND:-}"

[[ -z "${EXTRA:-}" ]]                       || { echo "too many arguments"; exit 1; }
[[ "${ENV_NAME:-}" =~ ^(stage|prod)$ ]]       || { echo "unknown environment"; exit 1; }
[[ "${SERVICE:-}" =~ ^(backend|frontend)$ ]]  || { echo "unknown service"; exit 1; }
# Stage only takes commit SHAs and prod only takes versions, so nothing untested reaches prod
if [[ "$ENV_NAME" == stage ]]; then
  [[ "${TAG:-}" =~ ^[0-9a-f]{40}$ ]]          || { echo "stage requires a commit SHA"; exit 1; }
else
  [[ "${TAG:-}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "prod requires a version tag"; exit 1; }
fi

# Backend and frontend may deploy at the same time and both edit .env
exec 9>/opt/mtogo/deploy.lock
flock 9

cd "/opt/mtogo/$ENV_NAME"
VAR="${SERVICE^^}_TAG"
OLD="$(grep "^$VAR=" .env | cut -d= -f2- || true)"

set_tag() { sed -i "s/^$VAR=.*/$VAR=$1/" .env; }
log()     { echo "$(date -u +%FT%TZ) $ENV_NAME $SERVICE $TAG $1" >> /opt/mtogo/deploy.log; }

set_tag "$TAG"
# The running container is untouched until the pull succeeds, so only the tag needs to go back
if ! docker compose pull "$SERVICE"; then
  set_tag "$OLD"
  log "failed, could not pull image"
  echo "Could not pull $TAG. $ENV_NAME keeps running ${OLD:-nothing}"
  exit 1
fi

if docker compose up -d --wait --wait-timeout 180 "$SERVICE"; then
  log ok
  docker image prune -af --filter "until=168h" > /dev/null
  echo "$SERVICE now runs $TAG in $ENV_NAME"
  exit 0
fi

echo "Health check failed for $TAG. Last log lines:"
docker compose logs --tail 50 "$SERVICE" || true

set_tag "$OLD"
if [[ -n "$OLD" ]]; then
  echo "Rolling back to $OLD"
  docker compose up -d "$SERVICE"
  log "failed, rolled back to $OLD"
else
  docker compose stop "$SERVICE"
  log "failed, no previous version"
fi
exit 1
