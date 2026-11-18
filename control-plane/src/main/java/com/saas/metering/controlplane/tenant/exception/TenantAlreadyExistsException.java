package com.saas.metering.controlplane.tenant.exception;

import com.saas.metering.controlplane.tenant.entity.Tenant;

public class TenantAlreadyExistsException extends RuntimeException {

    public TenantAlreadyExistsException(String name) {
        super("Tenant with name '%s' already exists".formatted(name));
    }
}
