package com.saas.metering.controlplane.plan.dto;

import com.saas.metering.controlplane.plan.utils.BillingPeriod;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PlanResponse(
        UUID id,
        String code,
        String name,
        long includedCalls,
        long overagePricePaise,
        String currency,
        BillingPeriod billingPeriod,
        boolean active,
        List<RateLimitRuleResponse> rateLimitRules,
        Instant createdAt,
        Instant updatedAt
) {
}
