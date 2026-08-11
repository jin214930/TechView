#!/usr/bin/env bash

set -Eeuo pipefail

umask 077

readonly APP_ROOT="/opt/techview"
readonly DEPLOY_DIR="${APP_ROOT}/deploy"
readonly RELEASE_DIR="${APP_ROOT}/releases"
readonly ENV_FILE="${APP_ROOT}/.env"
readonly COMPOSE_FILE="${DEPLOY_DIR}/docker-compose.prod.yml"
readonly METADATA_FILE="${RELEASE_DIR}/current.env"
readonly LOCK_FILE="${DEPLOY_DIR}/.deploy.lock"
readonly HEALTH_URL="http://127.0.0.1:8080/actuator/health"
readonly SMOKE_URL="http://127.0.0.1:8080/api/v1/auth/login"

NEW_IMAGE="${1:-}"
PREVIOUS_IMAGE=""
DEPLOYMENT_STARTED=false

log() {
  printf '[%s] [techview-deploy] %s\n' "$(date -u +'%Y-%m-%dT%H:%M:%SZ')" "$*"
}

fail() {
  log "ERROR: $*"
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "required command is missing: $1"
}

compose() {
  local image="$1"
  shift

  BACKEND_IMAGE="$image" docker compose \
    --project-name techview-prod \
    --env-file "$ENV_FILE" \
    --file "$COMPOSE_FILE" \
    "$@"
}

read_current_image() {
  if [[ ! -f "$METADATA_FILE" ]]; then
    return
  fi

  awk -F= '$1 == "CURRENT_IMAGE" { print substr($0, index($0, "=") + 1); exit }' "$METADATA_FILE"
}

inspect_running_image() {
  local container_id
  container_id="$(compose "$NEW_IMAGE" ps --quiet backend 2>/dev/null || true)"

  if [[ -n "$container_id" ]]; then
    docker inspect --format '{{.Config.Image}}' "$container_id" 2>/dev/null || true
  fi
}

wait_for_health() {
  local attempt
  local response

  for attempt in $(seq 1 24); do
    response="$(curl --silent --show-error --fail "$HEALTH_URL" 2>/dev/null || true)"
    if [[ "$response" == *'"status":"UP"'* ]]; then
      log "Actuator health check passed"
      return 0
    fi

    sleep 5
  done

  log "Actuator health check did not pass within 120 seconds"
  return 1
}

run_smoke_test() {
  local response_file
  local status_code

  response_file="$(mktemp)"
  status_code="$(curl \
    --silent \
    --show-error \
    --output "$response_file" \
    --write-out '%{http_code}' \
    --header 'Content-Type: application/json' \
    --data '{}' \
    "$SMOKE_URL" || true)"

  if [[ "$status_code" != "400" ]]; then
    log "API smoke test failed with HTTP ${status_code}"
    rm -f "$response_file"
    return 1
  fi

  rm -f "$response_file"
  log "API smoke test passed"
}

write_release_metadata() {
  local deployed_at
  local metadata_tmp

  deployed_at="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
  metadata_tmp="$(mktemp "${RELEASE_DIR}/.current.env.XXXXXX")"

  {
    printf 'CURRENT_IMAGE=%s\n' "$NEW_IMAGE"
    printf 'PREVIOUS_IMAGE=%s\n' "$PREVIOUS_IMAGE"
    printf 'DEPLOYED_AT=%s\n' "$deployed_at"
  } > "$metadata_tmp"

  chmod 600 "$metadata_tmp"
  mv -f "$metadata_tmp" "$METADATA_FILE"
}

prune_old_backend_images() {
  local image
  local images
  local repository

  repository="${NEW_IMAGE%:*}"

  if ! images="$(docker image ls \
    --filter "reference=${repository}:*" \
    --format '{{.Repository}}:{{.Tag}}')"; then
    log "WARNING: failed to list old backend images; cleanup will be retried after the next deployment"
    return 0
  fi

  while IFS= read -r image; do
    if [[ -z "$image" || "$image" == "$NEW_IMAGE" || "$image" == "$PREVIOUS_IMAGE" ]]; then
      continue
    fi

    log "removing old backend image ${image}"
    if ! docker image rm "$image"; then
      log "WARNING: failed to remove ${image}; cleanup will be retried after the next deployment"
    fi
  done <<< "$images"
}

rollback() {
  trap - ERR
  set +e

  log "deployment failed after container replacement; starting rollback"
  compose "$NEW_IMAGE" logs --no-color --tail 200 backend || true

  if [[ -z "$PREVIOUS_IMAGE" || "$PREVIOUS_IMAGE" == "$NEW_IMAGE" ]]; then
    log "no distinct previous image is available; stopping the failed backend"
    compose "$NEW_IMAGE" rm --stop --force backend || true
    return 1
  fi

  if ! docker image inspect "$PREVIOUS_IMAGE" >/dev/null 2>&1; then
    docker pull "$PREVIOUS_IMAGE" || return 1
  fi

  compose "$PREVIOUS_IMAGE" up --detach --no-deps --force-recreate backend || return 1
  wait_for_health || return 1

  log "rollback completed with image ${PREVIOUS_IMAGE}"
}

handle_error() {
  local exit_code=$?

  if [[ "$DEPLOYMENT_STARTED" == "true" ]]; then
    rollback || log "ERROR: automatic rollback failed; manual recovery is required"
  else
    log "deployment stopped before the running backend was replaced"
  fi

  exit "$exit_code"
}

trap handle_error ERR

[[ "$NEW_IMAGE" =~ ^ghcr\.io/[a-z0-9._/-]+:[a-f0-9]{40}$ ]] \
  || fail "image must be a lowercase GHCR path tagged with a 40-character commit SHA"

require_command awk
require_command curl
require_command docker
require_command flock
require_command seq

[[ -f "$ENV_FILE" ]] || fail "missing production environment file: ${ENV_FILE}"
[[ -f "$COMPOSE_FILE" ]] || fail "missing production compose file: ${COMPOSE_FILE}"

install -d -m 0700 "$RELEASE_DIR"
exec 9> "$LOCK_FILE"
flock --nonblock 9 || fail "another deployment is already running"

PREVIOUS_IMAGE="$(inspect_running_image)"
if [[ -z "$PREVIOUS_IMAGE" ]]; then
  PREVIOUS_IMAGE="$(read_current_image)"
fi

log "pulling image ${NEW_IMAGE}"
docker pull "$NEW_IMAGE"

log "ensuring PostgreSQL is healthy"
compose "$NEW_IMAGE" up --detach --wait --wait-timeout 90 postgres

log "running Flyway migration with the application image"
compose "$NEW_IMAGE" run --rm --no-deps \
  --env SPRING_PROFILES_ACTIVE=prod,migration \
  --env SPRING_MAIN_WEB_APPLICATION_TYPE=none \
  backend

DEPLOYMENT_STARTED=true
log "replacing backend container"
compose "$NEW_IMAGE" up --detach --no-deps --force-recreate backend

wait_for_health
run_smoke_test
write_release_metadata
prune_old_backend_images

DEPLOYMENT_STARTED=false
trap - ERR
log "deployment completed successfully"
