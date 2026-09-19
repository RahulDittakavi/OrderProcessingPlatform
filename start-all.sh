#!/usr/bin/env bash

set -e

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m'

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$ROOT_DIR/logs"
PID_FILE="$LOG_DIR/service-pids"
mkdir -p "$LOG_DIR"
: > "$PID_FILE"

echo -e "${BLUE}=== Step 1: Starting Docker Infrastructure Containers ===${NC}"
docker compose -f "$ROOT_DIR/docker-compose.yaml" up -d --wait

echo -e "${BLUE}=== Step 2: Packaging Services Individually ===${NC}"
SERVICES=("discovery-server" "product-service" "inventory-service" "order-service" "api-gateway")

for SERVICE in "${SERVICES[@]}"; do
    echo -e "${YELLOW}Building $SERVICE...${NC}"
    (cd "$ROOT_DIR/$SERVICE" && ./mvnw clean package -DskipTests)
done

JVM_OPTS="-Xms128m -Xmx256m"

echo -e "${BLUE}=== Step 3: Starting Discovery Server (Eureka) ===${NC}"
nohup java $JVM_OPTS -jar "$ROOT_DIR/discovery-server/target/discovery-server-0.0.1-SNAPSHOT.jar" > "$LOG_DIR/discovery-server.log" 2>&1 &
echo $! >> "$PID_FILE"
echo "Discovery Server starting... Logs: logs/discovery-server.log"

echo -e "${YELLOW}Waiting for Eureka server on port 8761...${NC}"
until curl -s http://localhost:8761/ > /dev/null; do
    sleep 3
done
echo -e "${GREEN}Eureka is up!${NC}"

echo -e "${BLUE}=== Step 4: Starting Core Services ===${NC}"

# Product Service
nohup java $JVM_OPTS -jar "$ROOT_DIR/product-service/target/product-service-0.0.1-SNAPSHOT.jar" > "$LOG_DIR/product-service.log" 2>&1 &
echo $! >> "$PID_FILE"
echo "Product Service started. Logs: logs/product-service.log"

# Inventory Service
nohup java $JVM_OPTS -jar "$ROOT_DIR/inventory-service/target/inventory-service-0.0.1-SNAPSHOT.jar" > "$LOG_DIR/inventory-service.log" 2>&1 &
echo $! >> "$PID_FILE"
echo "Inventory Service started. Logs: logs/inventory-service.log"

# Order Service
nohup java $JVM_OPTS -jar "$ROOT_DIR/order-service/target/order-service-0.0.1-SNAPSHOT.jar" > "$LOG_DIR/order-service.log" 2>&1 &
echo $! >> "$PID_FILE"
echo "Order Service started. Logs: logs/order-service.log"

echo -e "${YELLOW}Waiting 12 seconds before starting API Gateway...${NC}"
sleep 12

echo -e "${BLUE}=== Step 5: Starting API Gateway ===${NC}"
nohup java $JVM_OPTS -jar "$ROOT_DIR/api-gateway/target/api-gateway-0.0.1-SNAPSHOT.jar" > "$LOG_DIR/api-gateway.log" 2>&1 &
echo $! >> "$PID_FILE"
echo "API Gateway started. Logs: logs/api-gateway.log"

echo -e "${GREEN}=== All services launched! ===${NC}"