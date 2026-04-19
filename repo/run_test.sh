#!/usr/bin/env bash
# Single-entry test runner for the LexiBridge Operations Suite.
# Runs everything inside Docker containers so the host never installs Java,
# Maven, Node, or Playwright binaries.
#
# Phases:
#   maven     - Dockerized Maven unit + integration tests.
#               Includes FullEndpointCoverageIT which performs true no-mock
#               HTTP coverage against the live docker-compose stack.
#   vitest    - Dockerized Node (Vitest) frontend unit tests.
#   playwright- Dockerized Playwright end-to-end tests against the compose stack.
#
# Env:
#   PHASES=maven,vitest,playwright (default all)
#   KEEP_STACK_UP=true keeps docker compose running after the run.
#
# Exit code is 0 only if every selected phase succeeded.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MAVEN_IMAGE="maven:3.9.9-eclipse-temurin-21"
NODE_IMAGE="node:20-bookworm"
PLAYWRIGHT_IMAGE="mcr.microsoft.com/playwright:v1.47.0-jammy"
M2_CACHE_VOLUME="${M2_CACHE_VOLUME:-lexibridge_m2_cache}"
NPM_CACHE_VOLUME="${NPM_CACHE_VOLUME:-lexibridge_npm_cache}"
KEEP_STACK_UP="${KEEP_STACK_UP:-false}"
PHASES="${PHASES:-maven,vitest,playwright}"
APP_HOST="${APP_HOST:-host.docker.internal}"
APP_PORT="${APP_PORT:-8081}"
DB_HOST_FOR_TESTS="${DB_HOST_FOR_TESTS:-host.docker.internal}"

if ! docker info >/dev/null 2>&1; then
  echo "Docker is not reachable. Start Docker Desktop/Engine first." >&2
  exit 1
fi

# MSYS on Windows mangles absolute paths passed to docker; force them through literally.
export MSYS_NO_PATHCONV=1
# pwd -W on Git Bash returns a Windows-native path suitable for docker volume mounts.
if command -v cygpath >/dev/null 2>&1; then
  WIN_ROOT="$(cygpath -m "$ROOT_DIR")"
else
  WIN_ROOT="$(cd "$ROOT_DIR" && pwd -W 2>/dev/null || pwd)"
fi

has_phase() { [[ ",${PHASES}," == *",$1,"* ]]; }

compose_up=0
compose_down() {
  if [ "$compose_up" -eq 1 ] && [ "$KEEP_STACK_UP" != "true" ]; then
    echo "[run_test] Stopping docker compose stack..."
    docker compose down >/dev/null 2>&1 || true
  elif [ "$KEEP_STACK_UP" = "true" ] && [ "$compose_up" -eq 1 ]; then
    echo "[run_test] KEEP_STACK_UP=true, leaving docker compose stack running."
  fi
}
trap compose_down EXIT

ensure_compose() {
  if [ "$compose_up" -eq 1 ]; then return; fi
  echo "[run_test] Starting docker compose stack..."
  docker compose up --build -d
  compose_up=1
  wait_for_app
}

wait_for_app() {
  echo "[run_test] Waiting for app health at http://localhost:${APP_PORT}/actuator/health ..."
  for i in $(seq 1 90); do
    if curl -fsS "http://localhost:${APP_PORT}/actuator/health" >/dev/null 2>&1; then
      echo "[run_test] App is healthy."
      return 0
    fi
    sleep 2
  done
  echo "[run_test] App failed to become healthy." >&2
  docker compose logs --tail=120 app >&2 || true
  exit 1
}

run_maven() {
  echo "[run_test] Phase: maven"
  ensure_compose
  docker volume create "$M2_CACHE_VOLUME" >/dev/null

  # DB URL from inside the Maven container: use host.docker.internal which
  # maps to the host's exposed MySQL port (3306). If your environment does
  # not expose host.docker.internal, override DB_HOST_FOR_TESTS.
  local app_base="http://${APP_HOST}:${APP_PORT}"
  local db_url="jdbc:mysql://${DB_HOST_FOR_TESTS}:3306/lexibridge?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"

  docker run --rm \
    --add-host=host.docker.internal:host-gateway \
    -e APP_BASE_URL="$app_base" \
    -e DB_JDBC_URL="$db_url" \
    -e DB_USER=lexibridge \
    -e DB_PASSWORD=lexibridge \
    -e ADMIN_USERNAME=admin \
    -e ADMIN_PASSWORD="AdminPass2026!" \
    -e DEVICE_SHARED_SECRET="local-dev-shared-secret-2026" \
    -v "$WIN_ROOT:/workspace" \
    -v "$M2_CACHE_VOLUME:/root/.m2" \
    -v /var/run/docker.sock:/var/run/docker.sock \
    -e DOCKER_HOST=unix:///var/run/docker.sock \
    -w /workspace \
    "$MAVEN_IMAGE" mvn -B test
}

run_vitest() {
  echo "[run_test] Phase: vitest"
  docker volume create "$NPM_CACHE_VOLUME" >/dev/null
  docker run --rm \
    -v "$WIN_ROOT:/workspace" \
    -v "$NPM_CACHE_VOLUME:/root/.npm" \
    -w /workspace \
    "$NODE_IMAGE" \
    sh -c "npm install --no-audit --no-fund && npm run test:unit"
}

run_playwright() {
  echo "[run_test] Phase: playwright"
  ensure_compose
  docker run --rm \
    --add-host=host.docker.internal:host-gateway \
    -v "$WIN_ROOT:/workspace" \
    -w /workspace \
    -e APP_BASE_URL="http://${APP_HOST}:${APP_PORT}" \
    "$PLAYWRIGHT_IMAGE" \
    sh -c "npm install --no-audit --no-fund && npx playwright test"
}

has_phase maven      && run_maven
has_phase vitest     && run_vitest
has_phase playwright && run_playwright

echo "[run_test] All selected phases completed successfully."
