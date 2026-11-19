# SaaS Metering Engine

A multi-module Spring Boot system for usage-based billing of a SaaS platform. Tenants are onboarded, assigned a billing plan (included calls, overage price, rate-limit rules), and their API usage is metered and billed.

> **Status:** early development. `control-plane` (1.1, 1.2), `ingestion-service` (2.1, 2.2, 3.1) and `billing-worker` (4.1, 4.2) are implemented.

## Tech stack
- Java 21, Gradle (wrapper included), multi-module build
- Spring Boot 4.1.2: Web, Data JPA, Validation, Actuator
- PostgreSQL (runtime), H2 (tests)
- Redis (idempotency keys) and Apache Kafka (event stream) for ingestion
- springdoc-openapi 3.2.0 (Swagger UI)
- Lombok, JUnit 5, Mockito, MockMvc

## Modules
| Module | Purpose | State |
|---|---|---|
| `common-domain` | shared library for types used across services (`UsageEvent`) | in use |
| `control-plane` | admin API: tenants, plans, subscriptions | implemented |
| `ingestion-service` | receives usage events, dedups in Redis, publishes to Kafka | implemented |
| `billing-worker` | consumes Kafka usage events, aggregates per cycle, rates and invoices | implemented |

## Features implemented (control-plane)
- **Story 1.1, Tenant onboarding:** `POST /v1/tenants`. Names are unique, case-insensitive.
- **Story 1.2, Subscription and plan assignment:**
  - Plans with included calls, overage price in paise (₹2/call = 200) and rate-limit rules (`REQUESTS_PER_WINDOW`, `USAGE_PERCENT_ALERT`, `HARD_CAP`).
  - A tenant has at most one active subscription. Changing plan cancels the old subscription and starts a new one, so history is kept.
  - `FREE` and `PRO` plans are seeded on startup.
  - Rate-limit rules are stored and validated only. Enforcement is not built yet.

## Features implemented (ingestion-service)
- **Story 2.1, Idempotent ingestion:** `POST /v1/events/ingest` takes a `UsageEvent` (`eventId`, `tenantId`, `metricType`, `quantity >= 1`, `eventTimestamp`). `SETNX idempotent:event:{eventId}` with a 24h TTL drops duplicates. Both new and duplicate events return `202`; invalid payloads return `400`. Runs on Java 21 virtual threads.
- **Story 2.2, Kafka publication:** new events are published to topic `tenant-usage-events`, keyed by `tenantId` (per-tenant ordering), with `acks=all`. The request waits for the broker ack. If publishing fails, the Redis key is deleted and the caller gets `503` so a retry can succeed.
- **Quota limits (control-plane):** assigning a plan writes `quota:limit:{tenantId}:{metric}` (= `includedCalls`) when the plan has a `HARD_CAP` rule and deletes it otherwise; cancelling deletes it. Writes happen after the DB commit and can be turned off with `app.quota-cache.enabled=false` (tests do). The control-plane now needs Redis.
- **Story 3.1, Quota guard:** after the idempotency check and before Kafka, `quota:usage:{tenantId}:{metricType}:{YYYY-MM}` is incremented atomically (45-day TTL). If `quota:limit:{tenantId}:{metricType}` exists and the new total exceeds it, the counter is decremented, the idempotency key released, and `429` returned with no Kafka publish. No limit key means unlimited. On Kafka failure the quota reservation is rolled back too.
- Config (`ingestion-service/src/main/resources/application.properties`): `spring.data.redis.*`, `spring.kafka.bootstrap-servers`, `ingestion.kafka.topic`, `ingestion.kafka.publish-timeout`, `ingestion.idempotency.ttl`.

## Features implemented (billing-worker)
- **Story 4.1, Aggregation:** a Kafka listener (`billing-worker-group`, topic `tenant-usage-events`) writes `processed_events` and upserts `tenant_usage_aggregates` in one transaction. Redelivered events are skipped; the billing cycle is the UTC `YYYY-MM` of `eventTimestamp`. Failures retry 3 times, then go to `tenant-usage-events.DLT`.
- **Story 4.2, Rating and invoices:** `InvoiceService.generate(tenantId, cycle)` computes `max(0, consumed - included) * overageRate` per metric with `BigDecimal` (HALF_UP, scale 2) and stores an `Invoice` with `InvoiceLineItem`s. It reads plans from the control-plane's `subscriptions` and `plans` tables, so both must share a database. Calling it twice returns the existing invoice. Nothing triggers it yet (no scheduler or endpoint).

## API
Base path `/v1`. Errors are RFC 7807 `application/problem+json`.

| Method | Path | Purpose |
|---|---|---|
| POST | `/tenants` | register a tenant |
| POST | `/plans` | create a plan with rules |
| GET | `/plans` | list plans (`?active=true`) |
| GET | `/plans/{id}` | get a plan |
| PUT | `/plans/{id}` | update a plan (`code` is immutable, rules are replaced) |
| POST | `/tenants/{tenantId}/subscription` | assign `{"planCode": "PRO"}`, switching if one is active |
| GET | `/tenants/{tenantId}/subscription` | current active subscription |
| GET | `/tenants/{tenantId}/subscriptions` | history, newest first |
| DELETE | `/tenants/{tenantId}/subscription` | cancel the active subscription |
| POST | `/events/ingest` | ingestion-service: submit a usage event (202 / 400 / 503) |

Status codes: 400 validation, 404 not found, 409 conflict (duplicate, same plan, inactive plan, non-active tenant), 422 contradictory rate-limit rules.

Example:
```bash
curl -X POST localhost:8080/v1/tenants -H 'Content-Type: application/json' -d '{"name":"Globex Corp"}'
curl -X POST localhost:8080/v1/tenants/<tenantId>/subscription -H 'Content-Type: application/json' -d '{"planCode":"PRO"}'
```

Interactive docs, once the app is running:
- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

## Getting started
Prerequisites: JDK 21 (Gradle can fetch it via the toolchain) and a PostgreSQL database.

Build and test everything:
```bash
./gradlew build
```

Run only the tests:
```bash
./gradlew test
```

Run the ingestion service (needs Redis on `localhost:6379` and Kafka on `localhost:9092`, or override via properties):
```bash
./gradlew :ingestion-service:bootRun
```

Run the control plane. `application.properties` has no datasource yet, so pass one (adjust to your database):
```bash
./gradlew :control-plane:bootRun --args='--spring.datasource.url=jdbc:postgresql://localhost:5432/metering --spring.datasource.username=postgres --spring.datasource.password=postgres --spring.jpa.hibernate.ddl-auto=update'
```

Tests run on in-memory H2 and need no database. They use `app.seed-plans=false`; set it to `false` yourself if you want to skip plan seeding at runtime.

## Project layout
```
specs/                      spec-driven development: one spec per story
control-plane/src/main/java/com/saas/metering/controlplane/
  config/                   JPA auditing, OpenAPI config
  web/                      GlobalExceptionHandler (RFC 7807 errors)
  plan/ subscription/ tenant/
    controller/ service/ repository/
    entity/  dto/  mapper/  exception/  utils/
```
Request flow is `Controller` → `Service` → `Repository`. A service is the only class that uses its own repository; other features go through that service (for example `SubscriptionService` uses `TenantService` and `PlanService`). Entities are never returned directly: controllers return DTOs built by the mappers.

## Spec-driven development
Each story starts as a spec in [`specs/`](specs/), is confirmed, then implemented against it:
- [1.1 Tenant onboarding](specs/1.1-tenant-onboarding.md)
- [1.2 Subscription and plan assignment](specs/1.2-subscription-plan-assignment.md)
- [2.1 Idempotent usage event ingestion](specs/US-2.1-idempotent-ingestion.md)
- [2.2 Asynchronous Kafka event publication](specs/US-2.2-kafka-event-streaming.md)
- [3.1 Real-time quota guard](specs/US-3.1-real-time-quota-guard.md)
- [4.1 Kafka usage aggregation](specs/US-4.1-kafka-usage-aggregation.md)
- [4.2 Tiered invoice rating](specs/US-4.2-tiered-invoice-rating.md)

## Design notes
- **Money** is stored as integer paise (INR only, monthly billing only for now). No floating point.
- **One active subscription per tenant** is enforced with a pessimistic lock on the tenant row, not a database constraint, because H2 has no partial unique indexes. A concurrency test covers this.
- **Plan edits** apply to current subscribers directly; prices are not snapshotted per subscription.
- **Schema** comes from Hibernate. There is no Flyway or Liquibase yet.

## Known gaps
- No authentication or authorisation on any endpoint.
- No datasource or `ddl-auto` configured in the main `application.properties`.
- Rate limits are stored but not enforced; billing is not built, and nothing consumes the Kafka topic yet.
- Ingestion and the Kafka consumer are tested without live Redis and Kafka.
- Quota limits are synced to Redis only when a subscription is assigned or cancelled. Plan edits (`PUT /plans/{id}`) and subscriptions that existed before this change do not update `quota:limit:*`.
- Plans have one metric and no base fee, so invoices use a zero base fee and a single metric line.
- Ingestion does not check that `tenantId` exists or has an active subscription.
- Free Tier values (1,000 calls, hard cap, 60 requests per minute) are placeholders.
- No tenant get, list or update endpoints.
