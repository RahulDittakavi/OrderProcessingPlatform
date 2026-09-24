# Backend Development Path: Order Processing Platform

This project is your laboratory for learning backend development with Java 21, Spring Boot, MongoDB, PostgreSQL, Kafka, Eureka, and Spring Cloud Gateway. Work through the labs in order. For every lab, make one small change, run a focused test, and write down what you observed.

## 1. Start With A Baseline

From the repository root:

```bash
./start-all.sh
```

The API gateway listens on `http://localhost:9000`. Stop everything with:

```bash
./stop-all.sh
```

Run the existing tests before changing code:

```bash
for service in discovery-server product-service inventory-service order-service api-gateway; do
  (cd "$service" && ./mvnw test)
done
```

Read the [README](../README.md), then inspect these files in this order:

1. [ProductController](../product-service/src/main/java/com/rahul/ms/product/controller/ProductController.java) - HTTP endpoints and status codes.
2. [ProductService](../product-service/src/main/java/com/rahul/ms/product/service/ProductService.java) - application logic.
3. [ProductRepository](../product-service/src/main/java/com/rahul/ms/product/repository/ProductRepository.java) - persistence abstraction.
4. [OrderService](../order-service/src/main/java/com/rahul/ms/order/service/OrderService.java) - the cross-service use case.
5. [InventoryRepository](../inventory-service/src/main/java/com/rahul/ms/inventory/repository/InventoryRepository.java) - the atomic stock reservation.
6. [OrderEventPublisher](../order-service/src/main/java/com/rahul/ms/order/service/OrderEventPublisher.java) - asynchronous delivery and retries.

## 2. Follow One Request

Create a product through the gateway:

```bash
curl -i -X POST http://localhost:9000/api/products \
  -H 'Content-Type: application/json' \
  -d '{"name":"Keyboard","description":"Mechanical keyboard","price":49.99}'
```

Then retrieve it:

```bash
curl -i http://localhost:9000/api/products
```

Trace the request on paper:

```text
HTTP client
  -> API Gateway :9000
  -> Eureka chooses product-service
  -> ProductController
  -> ProductService
  -> ProductRepository
  -> MongoDB
```

Questions to answer:

- Which class decides the URL and HTTP method?
- Which layer knows MongoDB exists?
- Where is input validation performed?
- What response is returned for an unknown product ID?
- What should happen if MongoDB is unavailable?

Useful endpoints include `/swagger-ui.html` and `/actuator/health` on each service port.

## 3. Learning Labs

### Lab 1: HTTP and REST

Use the product API to exercise `POST`, `GET`, `PUT`, and `DELETE`.

Add tests for:

- successful creation returns `201`;
- invalid price or blank name returns `400`;
- missing product returns `404`;
- deletion returns `204`.

Learn: resources, verbs, status codes, request DTOs, response DTOs, and why domain objects should not automatically become API contracts.

Suggested test location: `product-service/src/test/java/com/rahul/ms/product`.

### Lab 2: Layered Design

Read the controller, service, repository, model, and exception handler. Add one small product rule, such as rejecting a price less than or equal to zero. Put the rule in the service or validation layer, then test it.

Learn: controller responsibilities, dependency injection, separation of concerns, and exception-to-response mapping.

### Lab 3: Persistence Choices

Compare the product and inventory services:

- Product uses MongoDB and a document repository.
- Inventory uses PostgreSQL and JPA.

Add an inventory lookup test, then explain when a relational database is more useful than a document database. Inspect the database records after a request instead of trusting only the Java response.

Learn: schemas, indexes, transactions, repository methods, and the cost of choosing a storage model.

### Lab 4: The Order Use Case

Seed inventory, create a product, then place an order through `/api/orders`. Trace this path in [OrderService](../order-service/src/main/java/com/rahul/ms/order/service/OrderService.java):

1. Fetch the product through `ProductClient`.
2. Reserve stock through `InventoryClient`.
3. Save the order in MongoDB.
4. Save an outbox record in the same order-service transaction.

Try an order larger than available stock. Verify that the reservation fails and inspect whether the final state is consistent.

Learn: orchestration, synchronous service calls, transaction boundaries, and partial failure.

### Lab 5: Concurrency and Correctness

The inventory reservation uses a conditional SQL update:

```sql
quantity = quantity - requested
where quantity >= requested
```

Write a test that attempts two reservations against the same limited stock. Confirm that stock never becomes negative and that only one reservation succeeds when stock is insufficient for both.

Then explain why a read-then-write implementation would be vulnerable to a race condition.

Learn: atomic updates, lost updates, isolation, and concurrency testing.

### Lab 6: Resilience

Stop product-service while order-service is running. Place an order and observe the retry and circuit-breaker behavior in [ProductClient](../order-service/src/main/java/com/rahul/ms/order/client/ProductClient.java).

Use the order-service actuator endpoints and logs to answer:

- How many attempts happen before failure?
- What does the fallback return?
- What happens after repeated failures?
- Is returning `404` for a downstream outage a good API contract?

Improve the error model so a downstream outage is distinguishable from a product that does not exist.

Learn: timeouts, retries, circuit breakers, fallbacks, and error semantics.

### Lab 7: Reliable Events

Inspect [OrderEventPublisher](../order-service/src/main/java/com/rahul/ms/order/service/OrderEventPublisher.java) and [OrderEventOutbox](../order-service/src/main/java/com/rahul/ms/order/entity/OrderEventOutbox.java).

Place an order, stop Kafka, and inspect the outbox record. Start Kafka again and verify that the event is retried and eventually marked as published.

Then investigate this design question: what happens if the process crashes after Kafka accepts the event but before the outbox row is marked published? Design a consumer that is safe to run more than once.

Learn: eventual consistency, at-least-once delivery, retries, backoff, idempotency, and the transactional outbox pattern.

### Lab 8: Production Readiness

Add or improve:

- correlation/request IDs in logs;
- consistent error responses;
- pagination for product and order listing;
- authentication and authorization at the gateway;
- timeouts for every outbound call;
- integration tests using Testcontainers;
- metrics for order success, stock failures, and event retries;
- graceful shutdown and readiness checks.

For each feature, record the failure or operational problem it addresses.

## 4. Test Progression

Use the narrowest test that proves the behavior:

1. Unit test: one class with collaborators mocked.
2. MVC test: controller status codes and JSON contracts.
3. Repository/integration test: real MongoDB or PostgreSQL behavior.
4. Service integration test: multiple Spring components together.
5. End-to-end test: gateway through persistence and messaging.
6. Concurrency test: multiple requests competing for the same stock.

The current test suite includes a useful inventory service test, but most application tests are context smoke tests. A good next milestone is to replace one smoke test with a real behavior test per service.

## 5. Backend Concepts To Learn While Coding

- **HTTP:** methods, status codes, headers, JSON, idempotency.
- **Spring:** component scanning, dependency injection, configuration, validation, transactions.
- **Persistence:** CRUD, query methods, indexes, transactions, migrations, optimistic/pessimistic concurrency.
- **Distributed systems:** service discovery, network failure, timeouts, retries, circuit breakers, eventual consistency.
- **Messaging:** topics, keys, consumer groups, delivery guarantees, replay, idempotent consumers.
- **Security:** authentication, authorization, secret management, input validation, least privilege.
- **Operations:** structured logs, health checks, metrics, tracing, deployment, rollback.

## 6. A Simple Study Routine

For each change:

1. State the behavior you want in one sentence.
2. Find the class that owns that behavior.
3. Write a failing focused test.
4. Make the smallest implementation change.
5. Run the focused test, then the service test suite.
6. Exercise the running system with `curl`.
7. Review logs and persisted data.
8. Write down one tradeoff and one follow-up question.

Do not move to the next lab until you can explain the request path, the data ownership, the failure behavior, and the test that proves it.

## 7. Capstone Ideas

Choose one after the labs:

- Add a second product line and order multiple items atomically.
- Add order states such as `PENDING`, `CONFIRMED`, and `CANCELLED`.
- Add cancellation that publishes a compensating inventory event.
- Add an idempotency key so retrying an order request cannot create duplicates.
- Add a Kafka consumer that records an audit trail.
- Add contract tests so clients detect incompatible API changes.
- Run the full platform in Docker instead of starting JARs from the host.

A capstone is complete when it has a documented API contract, automated tests for success and failure, observable logs/metrics, and a short explanation of its consistency and retry behavior.
