#!/usr/bin/env bash
#
# Start the local infrastructure stack for the Healthcare Interoperability
# Learning Platform: the application PostgreSQL, the FHIR PostgreSQL, and the
# HAPI FHIR R4 JPA server.
#
# Usage:
#   ./scripts/start-local.sh
#
# The script:
#   1. creates .env from .env.example when .env is missing
#   2. runs `docker compose up -d --wait`
#   3. polls the FHIR CapabilityStatement until HTTP 200 or the timeout
#   4. prints the FHIR URL, the application PostgreSQL connection details,
#      and the next command to run
# It exits non-zero when the daemon is unreachable or the stack does not
# become ready in time.
#
# Override the wait budget with START_LOCAL_TIMEOUT (seconds, default 600):
#   START_LOCAL_TIMEOUT=900 ./scripts/start-local.sh

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

timeout_seconds="${START_LOCAL_TIMEOUT:-600}"

if ! docker info >/dev/null 2>&1; then
  echo "ERROR: the Docker daemon is not reachable. Start Docker and retry." >&2
  exit 1
fi

if [[ ! -f .env ]]; then
  echo "No .env found; creating it from .env.example (local placeholders only)."
  cp .env.example .env
fi

set -a
# shellcheck disable=SC1091
. "$repo_root/.env"
set +a

fhir_port="${HAPI_FHIR_PORT:-8080}"
metadata_url="http://localhost:${fhir_port}/fhir/metadata"
app_db_host="${APP_DB_HOST:-localhost}"
app_db_port="${APP_DB_PORT:-5432}"

echo "Starting the local stack (docker compose up -d --wait, timeout ${timeout_seconds}s)..."
docker compose up -d --wait --wait-timeout "$timeout_seconds"

echo "Waiting for the FHIR CapabilityStatement at ${metadata_url} ..."
deadline=$(( $(date +%s) + timeout_seconds ))
until curl -fsS -o /dev/null "$metadata_url" 2>/dev/null; do
  if (( $(date +%s) >= deadline )); then
    {
      echo "ERROR: timed out after ${timeout_seconds}s waiting for HTTP 200 from ${metadata_url}"
      echo "Current container status:"
      docker compose ps
    } >&2
    exit 1
  fi
  sleep 2
done

echo
echo "Local stack is up."
echo "  FHIR server:            ${metadata_url}"
echo "  Application PostgreSQL: host=${app_db_host} port=${app_db_port} db=${APP_DB_NAME} user=${APP_DB_USER} (password in .env: APP_DB_PASSWORD)"
echo
echo "Next command (start the application with the dev profile):"
echo "  ./mvnw -pl healthcare-application spring-boot:run -Dspring-boot.run.profiles=dev"
