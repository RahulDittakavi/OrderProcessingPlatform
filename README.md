# Order Processing Platform

## Local development

Start the infrastructure and services with:

```bash
./start-all.sh
```

The script waits for the Compose-managed databases and Kafka broker to become
healthy before starting the Spring Boot services. Stop only this project's
processes and containers with:

```bash
./stop-all.sh
```

Connection settings can be overridden through environment variables, including
`MONGO_ROOT_PASSWORD`, `DATABASE_PASSWORD`, `EUREKA_URL`, and
`KAFKA_BOOTSTRAP_SERVERS`. Do not commit a `.env` file or production secrets.

Order events are written to a MongoDB outbox and retried until Kafka accepts
them. Inventory reservation is performed atomically in PostgreSQL to prevent
concurrent orders from overselling stock.