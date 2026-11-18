package com.saas.metering.controlplane.subscription.exception;

import com.saas.metering.controlplane.tenant.entity.Tenant;
import java.util.UUID;

public class SubscriptionNotFoundException extends RuntimeException {

    public SubscriptionNotFoundException(UUID tenantId) {
        super("Tenant '%s' has no active subscription".formatted(tenantId));
    }
}
