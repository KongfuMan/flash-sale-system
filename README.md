# FlashSale

An example Spring Boot REST API that uses an in-memory database and requires no external database or Docker.
The project includes JPA entities and repositories for flash-sale business tables, plus example endpoints for reading all orders.
The Flyway migration for the original greeting example has been removed; tables and migration history in existing databases are not deleted automatically.

## Requirements

- JDK 17 or later
- Maven 3.6.3 or later

Build all modules from the repository root, then run the API connected to the local Citus database:

```bash
mvn verify
SPRING_PROFILES_ACTIVE=dev java -jar flash-sale-api/target/flash-sale-api-0.0.1-SNAPSHOT.jar
```

The root `pom.xml` is the Maven reactor parent project: `message-contract` holds the message contracts,
while `flash-sale-api` and `order-consumer` each produce standalone applications; the consumer is responsible for database order reservations.
Running `mvn verify` from the root builds and tests all modules.

## Environment Configuration

`flash-sale-api/src/main/resources/application.properties` holds API configuration shared across all environments (including the default port, 8081).
`application-dev.properties`, `application-test.properties`, and `application-prod.properties`
hold environment-specific overrides.

Select an environment with a Spring profile, for example:

```bash
SPRING_PROFILES_ACTIVE=dev java -jar flash-sale-api/target/flash-sale-api-0.0.1-SNAPSHOT.jar
SPRING_PROFILES_ACTIVE=prod java -jar flash-sale-api/target/flash-sale-api-0.0.1-SNAPSHOT.jar
```

The standalone consumer uses its own `application*.properties` under `order-consumer/src/main/resources/`;
changing API configuration does not automatically change consumer configuration, so database and environment settings must be checked separately for each.
Redis Lua scripts and Flyway migrations are maintained only by `flash-sale-api`; the consumer does not run migrations, which must be completed by a controlled process before startup.

When no profile is specified, H2 is used and no order business tables exist. The shared IntelliJ `FlashSale` run configuration selects the
`dev` profile by default. Do not commit passwords or other secrets to configuration files;
provide them through environment variables or an external secret-management system.

By default, the development environment connects to the local Docker Citus coordinator at `localhost:5442/postgres`, using user
`postgres`. Override this with `DB_JDBC_URL`, `DB_USERNAME`, and `DB_PASSWORD`.
The current Citus schema still lacks `password_hash` and the order-item ID sequence; safely coordinate a migration before using related functionality.
The `dev` profile disables Flyway by default to avoid automatically modifying an existing database; when migrations are required, first confirm the target database,
then explicitly enable `spring.flyway.enabled`.
Before enabling Flyway against an existing database, inspect its existing schema and Flyway history. A separate development database is recommended.

## Production Component Examples

- `entity` and `repository` use Spring Data JPA and Hibernate to map flash-sale business tables.
- `@RestControllerAdvice` demonstrates request-validation error handling.
- Spring Security permits all anonymous requests and disables CSRF protection. The order endpoints have no authentication, are for demonstration only, and must not be used with production data.
- Actuator provides health checks and Micrometer metrics. Only the health check is exposed by default; the metrics endpoint is not publicly exposed and should be exposed only after authentication and authorization are configured.
- `flash-sale-api/src/main/resources/db/migration/V1__capture_business_schema.sql` is the PostgreSQL DDL baseline for the current business tables; Flyway is disabled by default and should run as a controlled deployment step instead of automatically modifying the database at application startup.
- `ExternalIntegrationClient` demonstrates external HTTPS requests, timeouts, and an optional in-process TTL cache; it is disabled by default and makes no request at startup. Configure it through `demo.integration.enabled`, `demo.integration.url`, `demo.integration.connect-timeout`, `demo.integration.request-timeout`, and `demo.integration.cache-ttl`. The cache is not distributed.

## Database Migrations

`V1__capture_business_schema.sql` was generated from the business tables on the existing coordinator and includes tables, constraints, and the order ID
sequence; it does not include business data, test tables, or Citus extension configuration. These tables are not currently configured for Citus distribution.
The new V1 baseline reuses the version number formerly used by the old greeting example and applies only to brand-new databases. For a new database, run
Flyway `migrate` as a controlled deployment step; after a successful migration, use `validate` and test on the same PostgreSQL/Citus versions.

Run the following commands for a new local PostgreSQL container (they apply only to that development container). First install the local modules required by the API
from the repository root; this step does not execute database migrations:

```bash
mvn -pl flash-sale-api -am -DskipTests install
mvn -f flash-sale-api/pom.xml org.flywaydb:flyway-maven-plugin:11.7.2:migrate -Dflyway.locations=filesystem:"$PWD"/flash-sale-api/src/main/resources/db/migration -Dflyway.url=jdbc:postgresql://127.0.0.1:55432/flashsale -Dflyway.user=flashsale -Dflyway.password=flashsale-local-dev
mvn -f flash-sale-api/pom.xml org.flywaydb:flyway-maven-plugin:11.7.2:validate -Dflyway.locations=filesystem:"$PWD"/flash-sale-api/src/main/resources/db/migration -Dflyway.url=jdbc:postgresql://127.0.0.1:55432/flashsale -Dflyway.user=flashsale -Dflyway.password=flashsale-local-dev
```

Existing development databases already contain business tables, so **V1 must not be executed directly**, or table creation will fail. The old V1/V2 and V3/V4 histories
are incompatible with the new V1; inspect the schema, backups, and `flyway_schema_history` first and devise a dedicated coordination strategy.
Do not blindly baseline or repair, and do not run clean against an existing database.
The `dev` profile currently does not enable Hibernate schema validation because the Citus schema update is incomplete.

`flash-sale-api/src/main/resources/db/dev/R__seed.sql` is a repeatable Flyway migration for development, generated from local Citus records, and is not loaded by production deployments
by default. Run it only against a dedicated development database while application writes are stopped, specifying both the schema and dev Flyway locations; the script contains
explicit order IDs and adjusts the sequence. The script is repeatable, but `ON CONFLICT DO NOTHING` does not repair existing inconsistent data.
The source data has no product-inventory field, so `total_inventory` uses a placeholder value of 0; original dates, expired orders, and reserved inventory quantities
are retained as a snapshot and may not be suitable for directly testing new orders. The seed does not include password hashes.
V1 includes `unit_price`, `password_hash`, and the order-item ID sequence, while retaining the other product columns; this baseline is for new databases only.
Subsequent Flyway runs against a database where the development seed has been applied must also include the `db/dev` location, or validation can fail because an applied migration is missing.

```bash
FLYWAY_URL=jdbc:postgresql://localhost:5442/postgres FLYWAY_USER=postgres FLYWAY_PASSWORD=postgres \
  mvn -f flash-sale-api/pom.xml org.flywaydb:flyway-maven-plugin:11.7.2:migrate \
  -Dflyway.locations=filesystem:"$PWD"/flash-sale-api/src/main/resources/db/migration,filesystem:"$PWD"/flash-sale-api/src/main/resources/db/dev
```

### GitLab CI/CD

`.gitlab-ci.yml` first runs Maven `verify`, then provides a manual production-migration job on the protected default branch;
the API and consumer JARs are published only after Flyway succeeds. This workflow does not start or deploy applications; the published artifacts are for a subsequent deployment process.
In GitLab, protect the default branch and the `production` environment, configure required approvals, and set protected, environment-scoped
CI/CD variables `FLYWAY_URL`, `FLYWAY_USER`, and `FLYWAY_PASSWORD`. Passwords must be masked/hidden,
and the database migration account must be separate from the application runtime account; the runner must be able to securely access the target database.

Before first use, inspect the target schema and Flyway history, especially old V1/V2 and V3/V4; do not automatically baseline, repair,
or clean. Validate production migrations first on the same database version as production and with representative data, and ensure migrations are compatible with still-running
older application versions. The application's current anonymous security configuration is unsuitable for production deployment.

## Grafana Monitoring

The `dev` profile exposes Micrometer metrics at `http://localhost:8081/actuator/prometheus`.
For Prometheus running in Docker Desktop, add the following to its `scrape_configs`:

```yaml
  - job_name: flashsale
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ["host.docker.internal:8081"]
```

After reloading the Prometheus configuration, import `monitoring/grafana/flashsale-dashboard.json` into Grafana
and select the appropriate Prometheus data source. The dashboard includes HTTP request rate, error rate, average latency, JVM, CPU,
connection-pool, and Tomcat HTTP worker-thread metrics. HTTP metrics require request traffic, and rate graphs require at least two scrapes.
The order-list latency graph shows the average, approximate p95/p99, and sampled rolling maximum; these values are based on the query time window
and can differ from whole-run results calculated by load-testing tools. Histograms increase the number of metrics time series.
Spring Security currently permits anonymous access, and the metrics endpoint is publicly exposed by default only in dev; protect management endpoints in other environments.

## Bean Discovery vs Bean Definition

**Bean Discovery** is a way for Spring to locate components that need registration. For example,
component scanning finds classes within its scan scope that have `@Component` or its derived annotations.
The project's `@SpringBootApplication` scans from the `com.webapps.flashsale` package by default,
so it can discover Spring components in that package and its subpackages.

**Bean Definition** is configuration in the Spring container that describes how to create and manage a Bean,
such as its type, name, scope, and creation method. After component scanning discovers a class, Spring
registers a Bean Definition for it; an `@Bean` method can also provide a Bean Definition.
Spring then creates instances and injects dependencies according to those definitions.

They are not mutually exclusive alternatives: **scanning is one way to discover and register Bean Definitions**.
In addition to scanning and `@Bean`, Spring can register definitions through imported configuration, auto-configuration, and other mechanisms.

```text
Scan @Component classes ─┐
                         ├─> Register Bean Definition ─> Create and manage Bean
Process @Bean methods ───┘
```

## @Component vs @Bean

| | `@Component` | `@Bean` |
| --- | --- | --- |
| Applied to | Classes | Methods on configuration classes |
| Registration | The class is usually discovered through component scanning | Spring processes a factory method in a configuration class |
| Creation | Spring constructs the instance from the class definition | The method creates and returns the instance |
| Typical use | Classes you write that Spring can construct | Third-party classes or objects that need a custom creation process |

For example, `@RestController` is a component annotation, so the example Controller below
can be registered through scanning:

```java
@RestController
public class ExampleController {
    // ...
}
```

Use `@Bean` to place a class that cannot be annotated directly under Spring management:

```java
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
```

`AppConfig` itself must also be discovered or imported by Spring before its `@Bean` method is processed.
Both examples are Spring-managed Beans and can be injected into other components through constructors.
By default, Beans are singletons within the container, though this can be changed through scope configuration.

Request the endpoint from another terminal:

```bash
curl http://localhost:8081/actuator/health
```

After starting the `dev` profile connected to an initialized PostgreSQL database, request the order list:

```bash
curl 'http://localhost:8081/api/v1/orders?page=0&size=20'
curl http://localhost:8081/api/v1/orders/1
```

The default H2 database has no business tables, so the order endpoints require a database with initialized business tables.
Orders are paginated in ascending ID order; `page` starts at 0, and `size` defaults to 20 with a maximum of 100. The response includes `orders`,
`page`, `size`, and `hasNext`, while each order's `items` contains its corresponding order items. The endpoint first queries the order page,
then queries order items in bulk; V1 provides the required index through the unique constraint on `order_items.order_id`. Existing databases are not modified automatically.
Fetching a nonexistent order by ID returns HTTP 404.

## Order Reservation

### Standalone Redis Cluster Inventory Primitive (Experimental)

`InventoryPrimitives` provides experimental Redis admission for the API, independent of the consumer's database `OrderService`, and does not preload automatically at startup.
`reserve(eventId, eventProductId, userId, idempotencyKey, payload)` is a standalone Java API: IDs are positive 64-bit integers, and keys are nonblank and at most 128 characters long.
For the same event, a retry with the same key and binding returns `REPLAY` for a version 3 marker and `LEGACY_REPLAY` for a version 1/2 marker; a different product or user returns `CONFLICT`. Retries are checked first, even if the event has ended or inventory is zero.
An initial success returns `RESERVED`, atomically decrementing inventory and writing an `outbox:<requestId>` envelope and an `idem:<idempotencyKey>` JSON
pending record in the same hash with one `HSET`. Version 3 records contain event, product, and user IDs; the original idempotency key; a deterministic requestId (SHA-256 over the UTF-8 event ID, colon, and original key);
and `PENDING` status. IDs are stored as strings, and `binding` and `message` objects are not redundantly stored. Version 1 and 2 records are still validated in their respective formats, but historical delivery cannot be confirmed, so matching retries require manual reconciliation.
The original key is sensitive data; Redis read and log access must be restricted.
The version 3 structure is defined and serialized by the Java `InventoryMarker` record; Lua validates that a record matches the request before writing and validates the stored record on retry.
The version 1 compatibility format is still validated by Lua. The field contract between Java and Lua is verified through Redis cluster contract tests, not enforced at compile time.
Idempotency records have no TTL and are not deleted automatically, so storage grows continuously; the outbox is conditionally deleted after a Kafka ACK. Unknown or corrupt idempotency records fail closed. Scripts do not read legacy `i:` or
`idem:<sha256>` markers; before switching, stop all writes and wait for in-flight operations to finish, then resume only after safely migrating and reconciling old markers.
Legacy writers cannot read version 3 records; before upgrading, stop all legacy writers and wait for in-flight operations to finish. Do not run versions together or roll back directly.
The same key is allowed for different events; the Kafka message UUID is determined by event ID and idempotency key, and the consumer uses the unprefixed UUID string as the global database
idempotency key. Other database writers must avoid using the same key; this design still requires verification through cross-system reconciliation.

These records are Redis hash fields, not a Redis Stream. The API writes the outbox, which is published by the enabled API relay background worker; the current design is not suitable for production or as actual order-inventory protection, and it does not guarantee eventual message delivery.

Before preloading, stop all database and Redis inventory writes for the event, wait for in-flight operations to finish, and reconcile the data; maintain quiescence throughout reads and
preloading because database reads and Redis writes are not atomic. Use operator credentials with database read-only permissions and restricted Redis permissions,
then run manually after checking the target environment (without starting HTTP, migrations, or schema updates):

The demonstration endpoint is `POST /api/v1/operator/events/{eventId}/inventory/prefill`: success returns 201; an existing key
returns 409; a missing event, no products, or more than 1,000 products returns 400. **The current endpoint allows anonymous access and is for demonstration only;**
production must first configure operator authentication, authorization, network isolation, and auditing. Calling the endpoint must also follow the write-stop requirements above.

```bash
mvn -pl flash-sale-api -am install -DskipTests
SPRING_PROFILES_ACTIVE=dev mvn -f flash-sale-api/pom.xml compile exec:java -Dexec.mainClass=com.webapps.inventorycli.InventoryPrefillCommand -Dexec.args="prefill --event-id 1"
```

In dev, `REDIS_CLUSTER_NODES` can override the default three cluster seed nodes; connections use Redis Cluster topology discovery.
The process reads `flash_sale_events` and `flash_sale_event_products`, with at most 1,000 products per event, and inventory is
`allocated_quantity - sold_quantity - reserved_quantity`. The new hash is `flashsale:inventory:v1:{event:<id>}`;
the `status` and `prod:<event_product_id>` fields are written in one Lua operation, without a TTL; existing keys are always refused rather than overwritten.
Legacy `p:` inventory fields in existing Redis hashes are not read by the new scripts; before switching, stop all writes, migrate and reconcile inventory fields, and do not run new and legacy writers together.
The status is a preload snapshot, and subsequent database status changes are not synchronized. Only a snapshot status of `RUNNING` can be decremented; missing keys, missing products,
invalid inventory, and insufficient inventory all return explicit results and are not initialized implicitly. It does not check event times, user limits, or orders, and provides no rebuild process.
The original `--event-id 1` command remains a compatible form of preloading.

An `UPCOMING` snapshot preloaded before the event begins does not switch automatically. Confirm the target database and Redis environment, stop all database and
Redis writes for the event, wait for in-flight operations to finish, set the database event status to `RUNNING`, then explicitly run the following while writes remain quiescent:

```bash
SPRING_PROFILES_ACTIVE=dev mvn -f flash-sale-api/pom.xml compile exec:java -Dexec.mainClass=com.webapps.inventorycli.InventoryPrefillCommand -Dexec.args="activate --event-id 1"
```

Activation first reads the database and requires `RUNNING` status, then uses Lua to set only an existing hash with `UPCOMING` or `RUNNING` status
to `RUNNING`; it does not create a key or reset inventory. Other statuses, missing keys, and wrong types all fail. Database reads and Redis updates
are not atomic, so writes must remain quiescent and results reconciled before resuming; later database status changes are not synchronized automatically. Cleanup or rebuilding also requires
stopping writes first and manually coordinating both stores, and cannot reset state while traffic is running. This primitive remains unsuitable for production.

Unit test: `mvn test -Dtest=InventoryPrimitivesTest -Dsurefire.failIfNoSpecifiedTests=false`. The optional cluster script test runs only when a cluster is explicitly specified,
uses a random event ID, deletes only keys it successfully creates itself, and does not access the database:
`mvn test -Dtest=InventoryClusterTest -Dsurefire.failIfNoSpecifiedTests=false -Dinventory.test.cluster.nodes=host1:7001,host2:7002,host3:7003`。
Use an isolated test cluster and restricted credentials; a test can leave random keys behind if it terminates abnormally.
CI must run the following marker contract tests against an isolated Redis Cluster; `inventory.test.cluster.required=true` prevents a silent skip when no cluster is configured.
The tests verify Java-serialized version 3 markers, Lua write and retry behavior, and legacy-marker handling; the standalone record unit tests do not require Redis:

```bash
mvn -pl flash-sale-api -am test -Dtest=InventoryMarkerTest,InventoryMarkerPrefixTest -Dsurefire.failIfNoSpecifiedTests=false -Dinventory.test.cluster.required=true -Dinventory.test.cluster.nodes=host1:7001,host2:7002,host3:7003
```

### Standalone Kafka Order Consumer

`mvn package` at the root produces `flash-sale-api/target/flash-sale-api-0.0.1-SNAPSHOT.jar` and the
standalone `order-consumer/target/order-consumer-0.0.1-SNAPSHOT.jar`.
The consumer does not start an HTTP service or scan API controllers. It uses `OrderService` to create orders and requires an initialized
PostgreSQL/Citus business database. For example (first create the Kafka topic and check database configuration):

```bash
SPRING_PROFILES_ACTIVE=dev SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092 \
  java -jar order-consumer/target/order-consumer-0.0.1-SNAPSHOT.jar
```

The default topic is `order-requests-v1` and the group is `reservation-consumer`; override them with
`RESERVATION_KAFKA_TOPIC` and `RESERVATION_KAFKA_GROUP_ID`. Topic auto-creation is disabled by default.
Multiple consumer instances using the same group share partitions; parallelism is limited by the number of partitions.

The message value is UTF-8 JSON, for example:

```json
{"message_id":"8c4eb4f3-860d-4da0-962d-15490e49dc7c","message_type":"ORDER_REQUESTED",
 "schema_version":2,"occurred_at":"2026-11-05T20:36:46.115907+00:00",
 "data":{"flash_sale_event_id":1,"user_id":3,"items":[{"event_product_id":2,"quantity":1}]}}
```

Producers and consumers can share the message-envelope and payload types in `com.webapps.flashsale.contract`.
The consumer explicitly dispatches by `message_type` and `schema_version` and currently accepts only version 2 of `ORDER_REQUESTED`;
all three data IDs are positive 64-bit integers. Unknown fields, types, or versions are rejected. `ORDER_CREATED` is a separate fact event
that this consumer neither processes nor publishes; the model does not automatically ensure a type matches its payload.
`OrderRequested` uses an `items` list, whose entries contain `event_product_id` and `quantity`; the model itself performs no business validation.
The consumer currently accepts only one product with a quantity of 1. Atomic multi-product reservation, inventory, payment, and idempotency handling are not implemented;
multiple products or other quantities are rejected and retried continuously, which can block a partition. Version 1 messages are also rejected; messages in the old topic must be handled before deployment.
The database generates the order ID and uses the `message_id` UUID string without the `kafka:` prefix as the global idempotency key; other writers must avoid key conflicts.
The Kafka offset is acknowledged only after the order transaction commits; duplicate delivery is handled by existing database idempotency constraints.
The new topic and fields are incompatible with legacy `order-created-events-v1` messages; create a new topic and arrange processing for old messages before switching,
and do not directly replay old messages or assume old offsets will migrate.
All errors, including format or business errors, retry indefinitely. Permanent failures
block the relevant partition and other partitions on the same consumer thread, so they must be monitored and handled manually; there is currently no dead-letter queue, result notification, or
automatic inventory compensation. Ensure Kafka message retention is sufficient and verify the target topic before processing.

`POST /api/v1/flash_sale_event/{eventId}/product/{eventProductId}` uses the `UserId` and
`Idempotency-Key` request headers to invoke Redis inventory reservation. An initial pre-deduction and matching retry of a version 3 marker return 202
`{"status":"PENDING"}`; this does not mean an order was created or a message was published. A matching retry of a version 1/2 marker returns 503 and requires manual reconciliation; historical delivery cannot be inferred. The result of a Redis timeout can be ambiguous, so do not blindly restore inventory.
This workflow is experimental only; do not use it in production or with inventory data that cannot be recovered.

### Redis Outbox and API Relay

The API writes the outbox rather than sending Kafka directly; the relay runs as a background worker inside `flash-sale-api` and is no longer built or deployed separately.
The relay is disabled by default; unless enabled, new outbox messages remain in Redis and are not published automatically.
After successful admission (`RESERVED` or `REPLAY`), the request thread only places a best-effort hint onto a capacity-limited in-memory queue and does not wait for Kafka;
periodic full scans still redeliver when the queue is full, a hint is lost, or the relay fails, so correctness does not depend on hints.

Each new successful pre-deduction writes the decremented `prod:<eventProductId>`, a persistent
`idem:<idempotencyKey>` marker, and `outbox:<requestId>` in the same event hash with one `HSET`. The outbox value is the complete Kafka message envelope,
not only a marker or pointer; `requestId` is determined by the event ID and original idempotency key. A matching version 3 retry returns accepted
without decrementing inventory again or creating a new message; acceptance means only admission, not Kafka delivery or order creation. A mismatched binding returns a conflict.
The marker is retained to support idempotency even after the acknowledged outbox field is deleted; do not expose original keys or envelopes to untrusted clients.

The relay worker first handles admission hints, reading only the stored value of the corresponding `outbox:<requestId>` and reusing the same publishing path;
if there is no hint within `relay.poll-ms`, it scans event hashes on currently available Redis Cluster masters. Both paths validate the outbox envelope and publish it
to the Kafka topic used by the consumer, using the message UUID as the Kafka key. After receiving broker acknowledgment, it atomically compare-deletes an
outbox field only if it remains exactly equal to the sent value; it retains the field for retry on send failure, timeout, or an ambiguous acknowledgment result.

This can provide at-least-once delivery only on the assumption that acknowledged Redis data is not lost or rolled back; it is neither a Redis-Kafka distributed transaction
nor exactly-once delivery. Duplicates can result from a crash after broker acknowledgment but before deletion, a timeout where sending actually succeeded, concurrent API instances with relay enabled, or duplicate scans;
consumers must handle them idempotently with stable message IDs and database uniqueness constraints. Kafka acknowledgment and persistence settings, consumer transactions, message retention,
and Redis persistence, replication, and failover policies all require independent verification; a Redis ACK itself does not guarantee data remains after a failure. Redis loss
or rollback, eviction, expiration, exhausted capacity, or external modification can break inventory, idempotency, or delivery; admission must stop and data must be reconciled manually, with no promise of automatic recovery.

Cluster scanning is not a snapshot: SCAN can duplicate or omit entries, and failover, unavailable nodes, and resharding can cause one traversal to miss fields. It is necessary to continuously
scan all current masters, refresh topology, and repeat scans after the topology stabilizes; a single empty scan cannot prove the queue is drained. Scan cost, large event
hashes, backlog, corrupt envelopes, unavailable Kafka, and permanent consumer failures all require monitoring and manual handling. The relay has no metrics or metrics endpoint, logs warnings only, and has no queue-depth metric, lease, retry limit, dead-letter queue,
alerting, cleanup, or reconciliation tools. Restrict Redis permissions, prohibit eviction and uncontrolled writes, plan memory, backup, and recovery, and continuously observe backlog and delivery latency.

During migration, first stop all API instances and new and legacy inventory writers, wait for in-flight operations to finish, and reconcile Redis, Kafka, and the database. Historical
`idem:` markers lack a reliable complete outbox envelope, are not replayed automatically, and cannot establish from the marker whether publication or order creation occurred. Manually
reconcile every entry and handle ambiguous outcomes; do not blindly redeliver or restore inventory. Retain verified idempotency state, coordinate legacy field formats and inventory migration, and enable
new writers and the relay only after verification is complete. Do not run legacy direct-publishing writers alongside it or roll back directly. Existing prefill always rejects existing keys;
it cannot overwrite an existing hash or serve as a migration or rebuild solution. Any manual rebuild requires backups and reconciliation during quiescence, and explicit handling of unfinished requests.

The relay worker uses the API's Redis Cluster connection configuration and Kafka producer configuration, and its topic must match the consumer. Before enabling it, configure Redis Cluster
and Kafka, and confirm broker replication factor, `min.insync.replicas`, and persistence policies. When the relay is enabled, it verifies that the producer uses
`acks=all`, idempotent sending, and string serializers; otherwise startup fails.

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `RELAY_ENABLED` | `false` | Explicitly enables the background relay worker |
| `RELAY_POLL_MS` | `1000` | Maximum scan wait time in milliseconds when there is no admission hint |
| `RELAY_TIMEOUT_MS` | `5000` | Timeout in milliseconds while waiting for a Kafka ACK |
| `RELAY_TRIGGER_QUEUE_CAPACITY` | `10000` | Admission hint queue capacity; hints are dropped when full and scanning is relied upon |
| `RELAY_TOPIC` | `order-requests-v1` | Kafka publishing topic |
| `REDIS_CLUSTER_NODES` | See dev configuration | Redis Cluster seed nodes |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Spring default | Kafka bootstrap servers |

For example, start the API in a development environment with the cluster and topic configured:

```bash
SPRING_PROFILES_ACTIVE=dev RELAY_ENABLED=true REDIS_CLUSTER_NODES=localhost:7001,localhost:7002,localhost:7003 \
  SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092 java -jar flash-sale-api/target/flash-sale-api-0.0.1-SNAPSHOT.jar
```

When multiple API instances are enabled, each runs the task without cross-instance leases or coordination, causing duplicate scans and possible duplicate publication; plan instance configuration and ensure consumer idempotency.
Provide credentials through secure environment variables or a secret-management system. The relay itself has no dedicated metrics or metrics endpoint, so the API's general Actuator metrics cannot be relied on to monitor relay backlog or delivery.

```bash
curl -X POST http://localhost:8081/api/v1/flash_sale_event/1/product/2 \
  -H 'UserId: 3' -H 'Idempotency-Key: unique-request-key'
```

Event ID, event product ID (`event_product_id`, not `product_id`), and user ID must be positive integers; the idempotency key must be nonblank and at most
128 characters. Conflict, no inventory, or a non-running event returns 409; a missing product returns 404; invalid input returns 400; unavailable inventory returns 503.
The endpoint does not verify that the user exists or enforce per-user purchase limits; the request headers have no authentication or authorization. The result of a Redis timeout can be ambiguous,
so it cannot be assumed that the pre-deduction did not occur. The standalone Kafka consumer's database reservation logic is unaffected by this API change.

## Simulated Payment

`POST /api/v1/orders/{orderId}/payments` simulates a successful payment; it does not contact a payment provider or transfer money. For example:

```json
{"paymentId": 123, "transactionId": "unique-transaction", "paymentMethod": "demo"}
```

`paymentId` is a caller-supplied positive 64-bit integer; `transactionId` is required, nonblank and at most 128 characters. `paymentMethod` is optional and at most 30 characters. A successful request returns `201` with payment ID, order ID, transaction ID, amount and `SUCCESS` status. Unknown orders return `404`; expired or non-pending orders, missing reserved inventory, and duplicate payment or transaction IDs return `409`. Repeating a successful request is rejected, not replayed. Invalid input returns `400`.

The amount is the **current** `flash_sale_event_products.flash_price` when inventory is updated, not a price captured at reservation: it can change between reservation and payment. The transaction locks the order, checks expiry using database time, moves one reserved unit to sold, records the payment and marks the order paid atomically. It requires the existing PostgreSQL-compatible business schema and uniqueness constraints; no schema or migration is created.

**This endpoint is public and has no authentication or authorization. Anyone who can reach it can mark an eligible order paid without paying. Do not expose it publicly or use it with production data.**

Example response:

```json
{"status":"UP"}
```

If port 8081 is already in use, specify another port:

```bash
java -jar flash-sale-api/target/flash-sale-api-0.0.1-SNAPSHOT.jar --server.port=8082
```

Run tests:

```bash
mvn test
```

`ReservationPostgresTest` is skipped by default. To run it, specify a new, empty temporary PostgreSQL database;
the test executes migrations, writes data, and creates triggers, so it cannot be used against an existing development or production database:

```bash
mvn test -Dtest=ReservationPostgresTest -Dsurefire.failIfNoSpecifiedTests=false \
  -Dreservation.test.url=jdbc:postgresql://localhost:PORT/postgres \
  -Dreservation.test.user=postgres -Dreservation.test.password=disposable
```

Each run requires a new empty database. The test checks that the `public` schema is empty before migration.

`RedisAdmissionIntegrationTest` is skipped unless `reservation.redis.disposable.port` is
set to the port of a new, empty disposable Redis on localhost. It writes reservation state
and must never point at an existing Redis instance. For example, start a disposable
container with a loopback-only dynamically assigned port, inspect its port with
`docker port`, and run `mvn test -Dtest=RedisAdmissionIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false
-Dreservation.redis.disposable.port=PORT`; stop the container afterward. The legacy
PostgreSQL integration test also needs an explicitly provisioned Redis admission state
before it can exercise the reservation endpoint.

## Suggested Reading Order

1. Root `pom.xml`: reactor modules; each module's `pom.xml` defines its own dependencies.
2. `flash-sale-api/src/main/java/`: API application entry point, controllers, and configuration.
3. `message-contract/src/main/java/`: message contracts.
4. `order-consumer/src/main/java/`: standalone consumer, message processing, and database reservation service.
5. `flash-sale-api/src/main/java/com/webapps/flashsale/relay/`: Redis outbox scanning and Kafka publishing tasks.

Spring MVC serializes `@RestController` return values as JSON; embedded
Tomcat receives HTTP requests. The service listens on port 8081 by default.

## Spring Bean Annotation Overview

```text
                    SPRING BEAN ANNOTATIONS
                              |
          +-------------------+-------------------+
          |                   |                   |
     Bean Discovery      Bean Definition    Dependency Injection
          |                   |                   |
     @Component             @Bean              @Autowired
          |                   |                @Qualifier
          |               @Configuration       @Primary
          |                   |                @Value
          |                   |                @Lazy
          |                   |
          |               @Import
          |               @Conditional
          |
          +-- @Service
          |
          +-- @Repository
          |
          +-- @Controller
          |      |
          |      +-- @RestController
          |
          +-- @Configuration
          |
          +-- Custom stereotype annotations


                    BEAN MANAGEMENT
                          |
          +---------------+---------------+
          |               |               |
       Scope          Lifecycle       Conditions
          |               |               |
        @Scope       @PostConstruct    @Conditional
        @Lazy        @PreDestroy       @Profile
                     @DependsOn        @ConditionalOnBean
                                       @ConditionalOnMissingBean
                                       @ConditionalOnClass
                                       @ConditionalOnProperty
```
