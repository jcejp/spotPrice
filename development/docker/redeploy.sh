#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

echo "==> Stopping running application (if any)..."
pkill -f "spring-boot:run" 2>/dev/null && echo "    Application stopped." || echo "    No running application found."

echo "==> Building project (mvn clean install)..."
cd "$PROJECT_ROOT"
./mvnw clean install

echo "==> Ensuring Docker services are up..."
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
