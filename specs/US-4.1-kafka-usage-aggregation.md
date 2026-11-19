# US-4.1: Idempotent Kafka Consumer & Usage Aggregation

## Metadata
- **Epic:** Epic 4 - Billing & Rating Engine
- **Module:** `billing-worker`
- **Status:** Ready for Implementation

## 1. Business Objective
As the billing engine, I want to consume `UsageEvent` records from Kafka and persist idempotent, aggregated totals per `(tenantId, metricType, billingCycle)` into the database so billing cycles can be rated accurately and audited.

## 2. Event Stream & Persistence Contract
- **Consumed Topic:** `tenant-usage-events` (consumer group `billing-worker-group`)
- **Consumer Reliability:** Manual offset acknowledgment (`AckMode.MANUAL` / `RECORD`) or Spring Kafka retry + Dead Letter Topic (DLQ) after transient failures.
- **Database Tables:**
    - `processed_events(event_id PRIMARY KEY, processed_at)` — guarantees consumer-side idempotency even if Redis TTL expires or a consumer rebalances.
    - `tenant_usage_aggregates(id, tenant_id, metric_type, billing_cycle, total_consumed, updated_at)` with unique constraint on `(tenant_id, metric_type, billing_cycle)`.

## 3. Technical Specification
- Derive `billing_cycle` in `YYYY-MM` format from `event.eventTimestamp()` (UTC) so late-arriving events land in the billing period when the usage actually occurred.
- Execute `processed_events` insert and `tenant_usage_aggregates` upsert inside a single `@Transactional` database operation.
- If `eventId` already exists in `processed_events`, skip incrementing `total_consumed` and acknowledge cleanly.

## 4. Acceptance Criteria
- [ ] Valid `UsageEvent` updates `total_consumed` for the matching `(tenantId, metricType, billingCycle)` and records `eventId` in `processed_events`.
- [ ] Re-delivering the same `UsageEvent` (e.g., after consumer restart before commit) does not double-count `total_consumed`.
- [ ] An event with a prior-month `eventTimestamp` updates that prior month's `billing_cycle` aggregate rather than the current wall-clock month.