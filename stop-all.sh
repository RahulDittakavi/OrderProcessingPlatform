#!/usr/bin/env bash

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_ARGS=(-f "$ROOT_DIR/docker-compose.yaml")
if [[ -f "$ROOT_DIR/docker-compose.override.yaml" ]]; then
	COMPOSE_ARGS+=(-f "$ROOT_DIR/docker-compose.override.yaml")
fi

echo "Stopping project containers..."
docker compose "${COMPOSE_ARGS[@]}" down

echo "All services and containers stopped. Persistent data volumes were kept."