#!/usr/bin/env bash
#
# Run the full Maven build and test suite: unit tests, ArchUnit checks, and the
# Testcontainers-based *IT tests under Failsafe.
#
# Docker must be running: the integration tests start PostgreSQL and HAPI FHIR
# containers.
#
# Usage:
#   ./scripts/run-tests.sh
#
# On a machine whose /etc/timezone names a zone the PostgreSQL image does not
# know (for example the stale Asia/Calcutta alias), export TZ=UTC first:
#   TZ=UTC ./scripts/run-tests.sh

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

if ! docker info >/dev/null 2>&1; then
  echo "ERROR: the Docker daemon is not reachable." >&2
  echo "The integration tests need Docker (Testcontainers). Start Docker and retry." >&2
  exit 1
fi

echo "Docker is available. Running ./mvnw verify (unit tests + Testcontainers integration tests)..."
./mvnw verify
