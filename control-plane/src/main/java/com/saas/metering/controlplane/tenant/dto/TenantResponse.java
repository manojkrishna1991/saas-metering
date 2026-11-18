package com.saas.metering.controlplane.tenant.dto;

import com.saas.metering.controlplane.tenant.utils.TenantStatus;
import java.time.Instant;
import java.util.UUID;

public record TenantResponse(
        UUID id,
        String name,
        TenantStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
