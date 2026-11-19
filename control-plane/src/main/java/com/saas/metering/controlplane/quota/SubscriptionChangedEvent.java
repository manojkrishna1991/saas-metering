package com.saas.metering.controlplane.quota;

import com.saas.metering.controlplane.plan.entity.Plan;

import java.util.UUID;

/** Published when a tenant's active plan changes. {@code plan} is null when the subscription was cancelled. */
public record SubscriptionChangedEvent(UUID tenantId, Plan plan) {
}
