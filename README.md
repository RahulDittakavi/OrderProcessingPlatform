# Order Processing Platform

A local microservices application for managing a product catalog, tracking
inventory, and placing orders. The services communicate through service
discovery and an API gateway; order events are published to Kafka.

## What is in the project

### Order Desk

A browser-based dashboard served by the API gateway at `http://localhost:9000/`.
It lets you:

- View the product catalog, SKU codes, prices, and available stock.
- Add products to the catalog.
- Place orders and see recent order statuses and totals.
- Refresh the dashboard data.

### Services

| Component | Default port | Responsibilities |
| --- | ---: | --- |
| API Gateway | 9000 | Serves the Order Desk and routes `/api/products/**`, `/api/inventory/**`, and `/api/orders/**` to discovered services. |
| Discovery Server | 8761 | Eureka registry used by the gateway and services. |
| Product Service | 8080 | Creates, lists, reads, updates, and deletes products stored in PostgreSQL. |
| Order Service | 8081 | Creates and lists orders; verifies product details, reserves inventory, and writes order events to an outbox. |
| Inventory Service | 8082 | Reads stock and atomically reserves inventory in PostgreSQL. |

### Main API endpoints

Requests below can be sent through the gateway at `http://localhost:9000`.

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/api/products` | Create a product. |
| `GET` | `/api/products` | List products. |
| `GET` | `/api/products/{id}` | Get one product. |
| `PUT` | `/api/products/{id}` | Update a product. |
| `DELETE` | `/api/products/{id}` | Delete a product. |
| `GET` | `/api/inventory?skuCode={sku}` | Check stock for one or more SKU codes. Repeat `skuCode` to check multiple items. |
| `GET` | `/api/inventory/{skuCode}` | Get stock for a SKU. |
| `PUT` | `/api/inventory/reduce-stock?skuCode={sku}&quantity={n}` | Reserve stock; provide an `Idempotency-Key` header. |
| `GET` | `/api/orders` | List orders. |
| `POST` | `/api/orders` | Place an order; provide an `Idempotency-Key` header. |

Order creation is idempotent: repeating a request with the same key and order
details returns the existing order instead of reserving stock again. Reusing
the key with a different product or quantity returns `409 Conflict`.
Inventory reservations also use an idempotency key to prevent a duplicate
reservation. Both order and inventory idempotency keys must be non-blank and
at most 128 characters.

The Order Service saves confirmed orders and their outbox events atomically in
PostgreSQL. A scheduled publisher claims pending outbox rows with
`FOR UPDATE SKIP LOCKED` and retries delivery to the Kafka topic
`order-placed-topic`. Delivery is at least once, so consumers should be
idempotent.

## Technology and local infrastructure

- Java 21 and Spring Boot microservices.
- Spring Cloud Gateway, Eureka, and LoadBalancer.
- PostgreSQL for products, orders/outbox data, and inventory (separate databases).
- Apache Kafka for order events.
- Prometheus and Grafana for application metrics and dashboards.
- Docker Compose for PostgreSQL, Kafka, the application services, and monitoring.
- Static HTML, CSS, and JavaScript for the Order Desk.

## Run locally

Prerequisites: Docker with the Compose plugin. `curl` is only required for the
optional health-check CLI. Java 21 and PostgreSQL are not required on the host;
the application images and database run in containers.

The local Compose configuration defaults to the `orderapp` database user and
password. For a different local password, export `DB_PASSWORD` before starting
the stack:

```bash
export DB_PASSWORD='your-local-orderapp-password'
```

Start the infrastructure and services:

```bash
./start-all.sh
```

The script builds the five Java service images and starts the complete stack:
PostgreSQL, Kafka, Eureka, the application services, Prometheus, and Grafana.
Compose waits for the database, broker, and application health checks before
reporting success; if startup fails, the script prints container status and
recent logs. Open `http://localhost:9000/` to use the Order Desk.

The databases are created automatically on the first startup. PostgreSQL data
is kept in a named Compose volume when the stack is stopped; `stop-all.sh` does
not delete it. The services can also be started directly with
`docker compose up --build --wait`.
PostgreSQL is published on host port `5433` by default so it can coexist with a
local PostgreSQL server on `5432`; set `POSTGRES_HOST_PORT` to change the host
port. The Compose database starts with a fresh volume and does not import data
from an existing host PostgreSQL instance; that host database is left untouched.

Prometheus is available at `http://localhost:9090`, and Grafana is available
at `http://localhost:3000` (default local login: `admin` / `admin`). The
pre-provisioned **Order Processing Platform** dashboard is in the **Platform**
folder. Set `GRAFANA_ADMIN_USER` and `GRAFANA_ADMIN_PASSWORD` before starting
Compose to override the local Grafana credentials. Prometheus scrapes
`/actuator/prometheus` from the gateway, discovery server, and all three
application services over the Compose network. Service ports remain published
on the host for local API access and the health-check CLI.

Stop this project's services and containers with:

```bash
./stop-all.sh
```

View service logs with `docker compose logs -f <service-name>` (for example,
`docker compose logs -f order-service`).

### Health-check CLI

The Go CLI checks the health endpoints of all five application services and
checks connectivity to PostgreSQL, Kafka, Prometheus, and Grafana. Run it from
the repository root:

```bash
cd tools/platform-check
go run . -timeout 3s
```

Each check has its own timeout. The command exits with status `1` if any
component is down, so it can also be used in scripts. Run its unit tests with
`go test ./...` from `tools/platform-check`. If PostgreSQL uses a non-default
host port, pass it with `-postgres-address localhost:<port>`.

## Configuration

Defaults are intended for local development. Common environment variable
overrides include:

| Variable | Used for |
| --- | --- |
| `DB_USER`, `DB_PASSWORD` | Compose PostgreSQL credentials; default to `orderapp` for local development. |
| `POSTGRES_HOST_PORT` | Host port mapped to PostgreSQL's container port 5432; defaults to `5433`. |
| `ORDER_DB_URL` | Order Service PostgreSQL URL; Compose sets it to `jdbc:postgresql://postgres:5432/orderdb`. |
| `INVENTORY_DB_URL` | Inventory Service PostgreSQL URL; Compose sets it to `jdbc:postgresql://postgres:5432/inventorydb`. |
| `PRODUCT_DB_URL` | Product Service PostgreSQL URL; Compose sets it to `jdbc:postgresql://postgres:5432/productdb`. |
| `EUREKA_URL` | Eureka registry URL; Compose points services to `discovery-server`. |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka broker address used by Order Service; Compose uses `kafka:29092`. |
| `KAFKA_ADVERTISED_HOST` | Host advertised by Kafka to clients connecting from outside Compose; defaults to `localhost`. |
| `GRAFANA_ADMIN_USER`, `GRAFANA_ADMIN_PASSWORD` | Local Grafana login; defaults to `admin` / `admin`. |
| `FLYWAY_BASELINE_ON_MIGRATE` | Set to `true` only when upgrading an existing inventory database that has schema objects but no Flyway history. |

Defaults are for local development only. Set non-default credentials for shared
environments, and do not commit a `.env` file or production credentials.

All three persistence services apply versioned Flyway migrations on startup and
check the resulting schema with Hibernate (`ddl-auto=validate`). Existing data
from removed stores is not automatically imported; the repository contains no
product seed/sample records that need recreating.

## Documentation and API exploration

The Order Service exposes OpenAPI documentation at
`http://localhost:8081/swagger-ui.html`. The backend learning notes are in
[`docs/BACKEND-LEARNING.md`](docs/BACKEND-LEARNING.md).

Import [`postman/OrderProcessingPlatform.postman_collection.json`](postman/OrderProcessingPlatform.postman_collection.json)
into Postman to try the APIs through the gateway. The collection defaults to
`http://localhost:9000`; start the local stack with `./start-all.sh` first.
Create a product with the seeded `iphone_15` inventory SKU before placing an
order. The create request stores the returned product ID for the later requests.
The delete-product request removes the product from the catalog.

## Continuous integration and database tests

The GitHub Actions workflow at `.github/workflows/ci.yml` builds and verifies
all five services, runs formatting, vet, and race-enabled tests for the Go
health-check CLI, checks startup-script syntax, validates Compose, and builds
the application container images on pushes to `main`, pull requests, and
manual runs. The order-service test suite includes a
Testcontainers integration test that starts PostgreSQL, applies the real Flyway
migrations, validates the Hibernate schema, and checks order persistence.
Running that test locally requires Docker:

```bash
cd order-service
./mvnw -Dapi.version=1.40 -Dtest=OrderPostgresIntegrationTest test
```

The complete CI verification can also be run locally by running
`./mvnw verify` inside each service directory. For `order-service`, use
`./mvnw -Dapi.version=1.40 verify` when your Docker engine requires API 1.40 or
newer.
