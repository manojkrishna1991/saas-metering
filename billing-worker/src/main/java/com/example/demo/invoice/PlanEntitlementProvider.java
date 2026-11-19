package com.example.demo.invoice;

import java.util.List;

public interface PlanEntitlementProvider {

    /** Entitlements of the plan the tenant was subscribed to during {@code billingCycle} (YYYY-MM); empty if none. */
    List<PlanEntitlement> findEntitlements(String tenantId, String billingCycle);
}
