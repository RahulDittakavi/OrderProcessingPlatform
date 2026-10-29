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
- A local PostgreSQL installation for application data; Docker Compose for Kafka
  and monitoring infrastructure.
- Static HTML, CSS, and JavaScript for the Order Desk.

## Run locally

Prerequisites: PostgreSQL installed on the host, Docker Compose, Java 21, and
`curl`. Go 1.22 or newer is only required for the optional health-check CLI.

Create the application role and databases in PostgreSQL (set its password
interactively with `\password orderapp`):

```sql
CREATE ROLE orderapp WITH LOGIN;
CREATE DATABASE orderdb OWNER orderapp;
CREATE DATABASE inventorydb OWNER orderapp;
CREATE DATABASE productdb OWNER orderapp;
```

Export the shared credentials before launching the services. `DB_PASSWORD` has
no default; service database URLs default to the local database names shown
above and can be overridden independently.

```bash
export DB_USER=orderapp
export DB_PASSWORD='your-local-orderapp-password'
```

Start the infrastructure and services:

```bash
./start-all.sh
```

The script starts Compose-managed infrastructure, builds the services, then
launches Eureka, the application services, and the API gateway. Open
`http://localhost:9000/` to use the Order Desk.

Prometheus is available at `http://localhost:9090`, and Grafana is available
at `http://localhost:3000` (default local login: `admin` / `admin`). The
pre-provisioned **Order Processing Platform** dashboard is in the **Platform**
folder. Set `GRAFANA_ADMIN_USER` and `GRAFANA_ADMIN_PASSWORD` before starting
Compose to override the local Grafana credentials. Prometheus scrapes
`/actuator/prometheus` from the gateway, discovery server, and all three
application services. The local monitoring containers use host networking so
Prometheus can scrape Spring services running directly on the Linux host.

Stop this project's services and containers with:

```bash
./stop-all.sh
```

Service logs are written to `logs/`.

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
`go test ./...` from `tools/platform-check`.

## Configuration

Defaults are intended for local development. Common environment variable
overrides include:

| Variable | Used for |
| --- | --- |
| `DB_USER`, `DB_PASSWORD` | Shared PostgreSQL role and password. The password must be supplied. |
| `ORDER_DB_URL` | Order Service PostgreSQL URL; defaults to `jdbc:postgresql://localhost:5432/orderdb`. |
| `INVENTORY_DB_URL` | Inventory Service PostgreSQL URL; defaults to `jdbc:postgresql://localhost:5432/inventorydb`. |
| `PRODUCT_DB_URL` | Product Service PostgreSQL URL; defaults to `jdbc:postgresql://localhost:5432/productdb`. |
| `EUREKA_URL` | Eureka registry URL. |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka broker address used by Order Service. |
| `KAFKA_ADVERTISED_HOST` | Host advertised by the Compose Kafka broker. |
| `GRAFANA_ADMIN_USER`, `GRAFANA_ADMIN_PASSWORD` | Local Grafana login; defaults to `admin` / `admin`. |
| `FLYWAY_BASELINE_ON_MIGRATE` | Set to `true` only when upgrading an existing inventory database that has schema objects but no Flyway history. |

Do not commit a `.env` file or production credentials.

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
all five services on pushes to `main`, pull requests, and manual runs. The
order-service test suite includes a Testcontainers integration test that starts
PostgreSQL, applies the real Flyway migrations, validates the Hibernate schema,
and checks order persistence. Running that test locally requires Docker:

```bash
cd order-service
./mvnw -Dapi.version=1.40 -Dtest=OrderPostgresIntegrationTest test
```

The complete CI verification can also be run locally by running
`./mvnw verify` inside each service directory. For `order-service`, use
`./mvnw -Dapi.version=1.40 verify` when your Docker engine requires API 1.40 or
newer.
