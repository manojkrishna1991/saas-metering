# US-2.2: Asynchronous Kafka Event Publication

## Metadata
- **Epic:** Epic 2 - Data Metering Plane
- **Module:** `ingestion-service`
- **Status:** Ready for Implementation

## 1. Business Objective
As the ingestion service, I want to publish deduplicated usage events asynchronously to Apache Kafka partitioned by `tenantId` so that high-volume bursts do not block HTTP threads and event ordering per tenant is preserved.

## 2. Event Stream Contract
- **Topic:** `tenant-usage-events`
- **Partition Key:** `tenantId` (ensures all events for a single tenant land on the same partition in order)
- **Payload:** Serialized `UsageEvent` JSON

## 3. Technical Specification
- Use `KafkaTemplate<String, UsageEvent>` configured with `acks=all` for durability.
- Publish inside `IngestionService` only *after* the Redis `setIfAbsent` deduplication check succeeds.
- Handle broker publish failures by logging the failure and rolling back (deleting) the Redis idempotency key so the client retry can succeed.

## 4. Acceptance Criteria
- [ ] Deduplicated event is published to topic `tenant-usage-events` with `tenantId` as the record key.
- [ ] If Kafka fails to acknowledge the message, the Redis idempotency key is deleted and a `503 Service Unavailable` is returned so the caller safely retries.