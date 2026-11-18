package com.saas.metering.controlplane.plan.exception;

import com.saas.metering.controlplane.plan.entity.Plan;

public class PlanNotFoundException extends RuntimeException {

    public PlanNotFoundException(String reference) {
        super("Plan '%s' not found".formatted(reference));
    }
}
