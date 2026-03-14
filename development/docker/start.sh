#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

echo "==> Starting development environment..."
cd "$SCRIPT_DIR"
docker compose up -d

echo "==> Waiting for PostgreSQL to be healthy..."
until docker inspect --format='{{.State.Health.Status}}' spotprice-postgres 2>/dev/null | grep -q "healthy"; do
    sleep 2
done
echo "    PostgreSQL is ready."

echo "==> Starting application (dev profile)..."
cd "$PROJECT_ROOT"
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
