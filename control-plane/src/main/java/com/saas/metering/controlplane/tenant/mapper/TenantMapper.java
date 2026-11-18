package com.saas.metering.controlplane.tenant.mapper;

import com.saas.metering.controlplane.tenant.entity.Tenant;
import com.saas.metering.controlplane.tenant.dto.TenantResponse;

public final class TenantMapper {

    private TenantMapper() {
    }

    public static TenantResponse toResponse(Tenant tenant) {
        return new TenantResponse(tenant.getId(), tenant.getName(), tenant.getStatus(),
                tenant.getCreatedAt(), tenant.getUpdatedAt());
    }
}
