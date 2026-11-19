# US-4.2: Multi-Metric Tiered Rating & Invoice Generation

## Metadata
- **Epic:** Epic 4 - Billing & Rating Engine
- **Module:** `billing-worker`
- **Status:** Ready for Implementation

## 1. Business Objective
As a SaaS finance and billing system, I want to calculate billable charges across multiple metrics (`api_calls`, `ai_tokens`, `storage_gb`) using each tenant's subscription plan allowances and overage rates with exact decimal precision.

## 2. Rating Formula & Precision Rules
- Use `java.math.BigDecimal` with explicit `MathContext` / `RoundingMode.HALF_UP` (scale `2` for currency, scale `6` for sub-cent unit rates)—never `double` or `float`.
- For each metric in the tenant's active plan for `billingCycle`:
    - `billableUnits = max(0, totalConsumed - includedAllowance)`
    - `metricCharge = BigDecimal.valueOf(billableUnits).multiply(overageRate)`
- `totalAmount = basePlanFee + sum(metricCharge across all metrics)`

## 3. Technical Specification
- Read `tenant_usage_aggregates` for `(tenantId, billingCycle)` and join/fetch plan entitlements (`includedAllowance`, `overageRate`, `basePlanFee`) from `control-plane` data/tables.
- Persist an `Invoice` record and child `InvoiceLineItem` records (`metricType`, `consumedUnits`, `includedUnits`, `billableUnits`, `lineAmount`) in a single transaction.

## 4. Acceptance Criteria
- [ ] Usage below or equal to `includedAllowance` produces `0.00` overage charge for that metric.
- [ ] Usage above `includedAllowance` computes exact `BigDecimal` overage without floating-point rounding drift.
- [ ] Generating an invoice for a tenant with multiple metrics (`api_calls`, `ai_tokens`, `storage_gb`) sums all line items plus the base fee accurately.