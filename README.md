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
| Product Service | 8080 | Creates, lists, reads, updates, and deletes products stored in MongoDB. |
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
reservation.

The Order Service writes order events to a MongoDB outbox and retries publishing
them to the Kafka topic `order-placed-topic` until Kafka accepts them.

## Technology and local infrastructure

- Java 21 and Spring Boot microservices.
- Spring Cloud Gateway, Eureka, and LoadBalancer.
- MongoDB for products and orders/outbox data.
- PostgreSQL for inventory.
- Apache Kafka for order events.
- Docker Compose for local databases and Kafka.
- Static HTML, CSS, and JavaScript for the Order Desk.

## Run locally

Prerequisites: Docker Compose, Java 21, and `curl`.

Start the infrastructure and services:

```bash
./start-all.sh
```

The script starts the Compose-managed databases and Kafka, builds the services,
then launches Eureka, the application services, and the API gateway. Open
`http://localhost:9000/` to use the Order Desk.

Stop this project's services and containers with:

```bash
./stop-all.sh
```

Service logs are written to `logs/`.

## Configuration

Defaults are intended for local development. Common environment variable
overrides include:

| Variable | Used for |
| --- | --- |
| `MONGO_ROOT_USERNAME`, `MONGO_ROOT_PASSWORD` | MongoDB credentials for Compose. |
| `MONGODB_HOST`, `MONGODB_PORT`, `MONGODB_DATABASE`, `MONGODB_USERNAME`, `MONGODB_PASSWORD`, `MONGODB_AUTH_DATABASE` | MongoDB connection settings for the services. |
| `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | Inventory PostgreSQL connection. |
| `EUREKA_URL` | Eureka registry URL. |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka broker address used by Order Service. |
| `KAFKA_ADVERTISED_HOST` | Host advertised by the Compose Kafka broker. |
| `FLYWAY_BASELINE_ON_MIGRATE` | Set to `true` only when upgrading an existing inventory database that has schema objects but no Flyway history. |

Do not commit a `.env` file or production credentials.

The Inventory Service applies versioned Flyway migrations on startup and checks
the resulting schema with Hibernate. For a new database, no manual schema setup
is required. When upgrading a database created before Flyway was introduced,
back up the database and set `FLYWAY_BASELINE_ON_MIGRATE=true` for the first
startup so Flyway records the existing schema at baseline version `0`; review
the migration and verify the existing tables before doing so.

## Documentation and API exploration

The Order Service exposes OpenAPI documentation at
`http://localhost:8081/swagger-ui.html`. The backend learning notes are in
[`docs/BACKEND-LEARNING.md`](docs/BACKEND-LEARNING.md).
