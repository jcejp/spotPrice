#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "==> Stopping application (if running via Maven)..."
# Kill any Spring Boot process started from this project
pkill -f "spring-boot:run" 2>/dev/null && echo "    Application stopped." || echo "    No running application found."

echo "==> Stopping Docker services..."
cd "$SCRIPT_DIR"
docker compose down

echo "==> Done."
