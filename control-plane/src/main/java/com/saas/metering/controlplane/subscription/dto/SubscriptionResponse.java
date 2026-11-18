package com.saas.metering.controlplane.subscription.dto;

import com.saas.metering.controlplane.subscription.utils.SubscriptionStatus;
import com.saas.metering.controlplane.plan.dto.PlanResponse;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionResponse(
        UUID id,
        UUID tenantId,
        SubscriptionStatus status,
        Instant startDate,
        Instant endDate,
        PlanResponse plan
) {
}
