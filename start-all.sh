#!/usr/bin/env bash

set -euo pipefail

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m'

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$ROOT_DIR/logs"
PID_FILE="$LOG_DIR/service-pids"
STARTUP_TIMEOUT_SECONDS=120
JVM_OPTS=(-Xms128m -Xmx256m)
mkdir -p "$LOG_DIR"
: "${DB_PASSWORD:?Set DB_PASSWORD for the local PostgreSQL orderapp role before starting services.}"
: > "$PID_FILE"

cleanup_on_failure() {
    local exit_code=$?
    if (( exit_code != 0 )) && [[ -f "$PID_FILE" ]]; then
        echo -e "${YELLOW}Startup failed; stopping services launched by this script.${NC}" >&2
        while read -r pid; do
            [[ -z "$pid" ]] || kill "$pid" 2>/dev/null || true
        done < "$PID_FILE"
        rm -f "$PID_FILE"
    fi
    return "$exit_code"
}
trap cleanup_on_failure EXIT

wait_for_health() {
    local service="$1"
    local endpoint="$2"
    local log_file="$3"
    local deadline=$((SECONDS + STARTUP_TIMEOUT_SECONDS))
    local body

    echo -e "${YELLOW}Waiting up to ${STARTUP_TIMEOUT_SECONDS}s for ${service}...${NC}"
    while (( SECONDS < deadline )); do
        if body="$(curl --connect-timeout 2 --max-time 5 --fail --silent "$endpoint" 2>/dev/null)" &&
            [[ "$body" =~ \"status\"[[:space:]]*:[[:space:]]*\"UP\" ]]; then
            echo -e "${GREEN}${service} is healthy.${NC}"
            return 0
        fi
        sleep 2
    done

    echo -e "${YELLOW}Timed out waiting for ${service} at ${endpoint}.${NC}" >&2
    if [[ -f "$log_file" ]]; then
        echo -e "${YELLOW}Recent ${service} log output:${NC}" >&2
        tail -n 40 "$log_file" >&2 || true
    fi
    return 1
}

echo -e "${BLUE}=== Step 1: Starting Docker Infrastructure Containers ===${NC}"
COMPOSE_ARGS=(-f "$ROOT_DIR/docker-compose.yaml")
if [[ -f "$ROOT_DIR/docker-compose.override.yaml" ]]; then
    COMPOSE_ARGS+=(-f "$ROOT_DIR/docker-compose.override.yaml")
fi
docker compose "${COMPOSE_ARGS[@]}" up -d --wait

echo -e "${BLUE}=== Step 2: Packaging Services Individually ===${NC}"
SERVICES=("discovery-server" "product-service" "inventory-service" "order-service" "api-gateway")

for SERVICE in "${SERVICES[@]}"; do
    echo -e "${YELLOW}Building $SERVICE...${NC}"
    (cd "$ROOT_DIR/$SERVICE" && ./mvnw clean package -DskipTests)
done

echo -e "${BLUE}=== Step 3: Starting Discovery Server (Eureka) ===${NC}"
nohup java "${JVM_OPTS[@]}" -jar "$ROOT_DIR/discovery-server/target/discovery-server-0.0.1-SNAPSHOT.jar" > "$LOG_DIR/discovery-server.log" 2>&1 &
echo $! >> "$PID_FILE"
echo "Discovery Server starting... Logs: logs/discovery-server.log"

wait_for_health "Discovery Server" "http://localhost:8761/actuator/health" "$LOG_DIR/discovery-server.log"

echo -e "${BLUE}=== Step 4: Starting Core Services ===${NC}"

# Product Service
nohup java "${JVM_OPTS[@]}" -jar "$ROOT_DIR/product-service/target/product-service-0.0.1-SNAPSHOT.jar" > "$LOG_DIR/product-service.log" 2>&1 &
echo $! >> "$PID_FILE"
echo "Product Service started. Logs: logs/product-service.log"

# Inventory Service
nohup java "${JVM_OPTS[@]}" -jar "$ROOT_DIR/inventory-service/target/inventory-service-0.0.1-SNAPSHOT.jar" > "$LOG_DIR/inventory-service.log" 2>&1 &
echo $! >> "$PID_FILE"
echo "Inventory Service started. Logs: logs/inventory-service.log"

# Order Service
nohup java "${JVM_OPTS[@]}" -jar "$ROOT_DIR/order-service/target/order-service-0.0.1-SNAPSHOT.jar" > "$LOG_DIR/order-service.log" 2>&1 &
echo $! >> "$PID_FILE"
echo "Order Service started. Logs: logs/order-service.log"

wait_for_health "Product Service" "http://localhost:8080/actuator/health" "$LOG_DIR/product-service.log"
wait_for_health "Inventory Service" "http://localhost:8082/actuator/health" "$LOG_DIR/inventory-service.log"
wait_for_health "Order Service" "http://localhost:8081/actuator/health" "$LOG_DIR/order-service.log"

echo -e "${BLUE}=== Step 5: Starting API Gateway ===${NC}"
nohup java "${JVM_OPTS[@]}" -jar "$ROOT_DIR/api-gateway/target/api-gateway-0.0.1-SNAPSHOT.jar" > "$LOG_DIR/api-gateway.log" 2>&1 &
echo $! >> "$PID_FILE"
echo "API Gateway started. Logs: logs/api-gateway.log"
wait_for_health "API Gateway" "http://localhost:9000/actuator/health" "$LOG_DIR/api-gateway.log"

echo -e "${GREEN}=== All services launched! ===${NC}"