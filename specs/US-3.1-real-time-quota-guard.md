# US-3.1: Real-Time Atomic Quota Counter & Limit Enforcement

## Metadata
- **Epic:** Epic 3 - Real-Time Quota Enforcement
- **Module:** `ingestion-service` (with quota cache populated by `control-plane`)
- **Status:** Ready for Implementation

## 1. Business Objective
As a SaaS platform operator, I want to track a tenant's running consumption per metric and billing period in Redis so hard usage limits are enforced in sub-10ms without querying the relational database on every event.

## 2. Redis Data Model
- **Usage Counter Key:** `quota:usage:{tenantId}:{metricType}:{YYYY-MM}` (e.g., `quota:usage:tenant_globex_01:api_calls:2026-11`)
- **Configured Hard Limit Key:** `quota:limit:{tenantId}:{metricType}` (stored as stringified integer; if absent, treat as unlimited / metered-only)
- **Counter TTL:** Set expiry to cover the active billing cycle plus a buffer (e.g., 45 days) so stale keys auto-clean without breaking late-month audits.

## 3. Technical Specification
- **Concurrency:** Runs on Java 21 Virtual Threads (`spring.threads.virtual.enabled=true`).
- **Atomic Increment:** Use `StringRedisTemplate.opsForValue().increment(usageKey, event.quantity())` after idempotency passes and before publishing to Kafka.
- **Limit Evaluation:**
    - Read `quota:limit:{tenantId}:{metricType}`. If present and `newTotal > hardLimit`:
        - Decrement back (`opsForValue().decrement(usageKey, event.quantity())`) or reject immediately.
        - Throw `ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Quota exceeded for metric")`.
- **Order of Operations in `IngestionService`:**
    1. Idempotency check (`idempotent:event:{eventId}`)
    2. Atomic quota check & increment (`quota:usage:...`)
    3. Kafka publish (`tenant-usage-events`) with rollback on failure

## 4. Acceptance Criteria
- [ ] Event within limit increments Redis counter atomically and proceeds to Kafka (`202 Accepted`).
- [ ] Event pushing total past `quota:limit:{tenantId}:{metricType}` returns `429 Too Many Requests` and does not publish to Kafka.
- [ ] Missing `quota:limit` key allows ingestion without blocking (unbounded / overage-billed metric).