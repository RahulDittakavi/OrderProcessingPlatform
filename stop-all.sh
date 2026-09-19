#!/usr/bin/env bash

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PID_FILE="$ROOT_DIR/logs/service-pids"

echo "Stopping project Spring Boot services..."
if [[ -f "$PID_FILE" ]]; then
	while read -r pid; do
		[[ -z "$pid" ]] || kill "$pid" 2>/dev/null || true
	done < "$PID_FILE"
	rm -f "$PID_FILE"
fi

echo "Stopping Docker containers..."
docker compose -f "$ROOT_DIR/docker-compose.yaml" down

echo "All services and containers stopped."