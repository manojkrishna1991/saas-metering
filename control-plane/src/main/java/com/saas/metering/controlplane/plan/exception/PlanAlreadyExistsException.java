package com.saas.metering.controlplane.plan.exception;

import com.saas.metering.controlplane.plan.entity.Plan;

public class PlanAlreadyExistsException extends RuntimeException {

    public PlanAlreadyExistsException(String code) {
        super("Plan with code '%s' already exists".formatted(code));
    }
}
