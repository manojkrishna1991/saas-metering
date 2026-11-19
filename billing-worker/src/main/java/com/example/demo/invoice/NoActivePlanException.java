package com.example.demo.invoice;

public class NoActivePlanException extends RuntimeException {
    public NoActivePlanException(String tenantId, String billingCycle) {
        super("Tenant %s has no plan for billing cycle %s".formatted(tenantId, billingCycle));
    }
}
