package com.saas.metering.controlplane.tenant.exception;

import com.saas.metering.controlplane.tenant.entity.Tenant;
import java.util.UUID;

public class TenantNotFoundException extends RuntimeException {

    public TenantNotFoundException(UUID id) {
        super("Tenant '%s' not found".formatted(id));
    }
}
