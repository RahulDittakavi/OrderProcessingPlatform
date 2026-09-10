#!/usr/bin/env bash

echo "Stopping running Spring Boot services..."
pkill -f "inventory-service" || true
pkill -f "order-service" || true
pkill -f "product-service" || true
pkill -f "discovery-server" || true
pkill -f "api-gateway" || true
pkill -f "spring-boot:run" || true

echo "Stopping Docker containers..."
docker compose down

echo "All services and containers stopped."