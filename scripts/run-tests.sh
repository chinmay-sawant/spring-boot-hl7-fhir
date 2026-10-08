#!/usr/bin/env bash
#
# Run the full Maven build and test suite: unit tests, the ArchUnit checks, and
# the *IT tests under Failsafe.
#
# The workflow tests run fully in-process: H2 in memory for the application
# database and an in-memory HAPI FHIR server inside the test JVM. Only
# DatabaseMigrationIT needs Docker, because it verifies the Flyway migrations
# against a disposable PostgreSQL container.
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
  echo "DatabaseMigrationIT needs Docker (Testcontainers). Start Docker and retry." >&2
  echo "Without Docker you can still run the workflow test on its own:" >&2
  echo "  ./mvnw verify -Dit.test=PatientWorkflowIT" >&2
  exit 1
fi

echo "Running ./mvnw verify (unit tests + integration tests)..."
./mvnw verify
