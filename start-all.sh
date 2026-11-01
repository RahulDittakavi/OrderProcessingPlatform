#!/usr/bin/env bash

set -euo pipefail

BLUE='\033[0;34m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
NC='\033[0m'

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_ARGS=(-f "$ROOT_DIR/docker-compose.yaml")
if [[ -f "$ROOT_DIR/docker-compose.override.yaml" ]]; then
    COMPOSE_ARGS+=(-f "$ROOT_DIR/docker-compose.override.yaml")
fi

echo -e "${BLUE}=== Building and starting the Order Processing Platform ===${NC}"
if ! docker compose "${COMPOSE_ARGS[@]}" up --build --detach --wait --wait-timeout 300; then
    echo -e "${YELLOW}The platform did not become healthy. Recent container logs:${NC}" >&2
    docker compose "${COMPOSE_ARGS[@]}" ps >&2 || true
    docker compose "${COMPOSE_ARGS[@]}" logs --tail=40 >&2 || true
    exit 1
fi

echo -e "${GREEN}=== All services are healthy! ===${NC}"
echo "Order Desk:  http://localhost:9000/"
echo "Prometheus:  http://localhost:9090"
echo "Grafana:     http://localhost:3000"
