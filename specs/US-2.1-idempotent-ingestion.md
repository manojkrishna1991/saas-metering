# US-2.1: Idempotent Usage Event Ingestion

## Metadata
- **Epic:** Epic 2 - Data Metering Plane
- **Module:** `ingestion-service`
- **Status:** Ready for Implementation

## 1. Business Objective
As a tenant application or backend orchestrator, I want to submit polymorphic telemetry events (`api_calls`, `ai_tokens`, `storage_gb`) with a unique `eventId` so that network retries never result in duplicate billing.

## 2. API Contract
- **Endpoint:** `POST /v1/events/ingest`
- **Request Body:** `UsageEvent` JSON payload
- **Responses:**
    - `202 Accepted`: Event validated and accepted (or safely ignored as a duplicate).
    - `400 Bad Request`: Missing required fields or `quantity < 1`.

## 3. Technical Specification
- **Concurrency:** Execute on Java 21 Virtual Threads (`spring.threads.virtual.enabled=true`).
- **Atomic Deduplication (Redis):**
    - Use `StringRedisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofHours(24))` (`SETNX` + TTL).
    - Key format: `idempotent:event:{eventId}`.
    - If `setIfAbsent` returns `false` (key exists), short-circuit immediately and return `202 Accepted` without forwarding downstream.

## 4. Acceptance Criteria
- [ ] Valid new `UsageEvent` writes `idempotent:event:{eventId}` to Redis with a 24h TTL and returns `202 Accepted`.
- [ ] Duplicate `eventId` within 24 hours returns `202 Accepted` without triggering downstream publishing.
- [ ] Payload with `quantity <= 0` or null `eventId` returns `400 Bad Request`.