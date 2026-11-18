package com.saas.metering.controlplane.tenant.service;

import com.saas.metering.controlplane.tenant.entity.Tenant;
import com.saas.metering.controlplane.tenant.exception.TenantAlreadyExistsException;
import com.saas.metering.controlplane.tenant.exception.TenantNotFoundException;
import com.saas.metering.controlplane.tenant.utils.TenantStatus;
import com.saas.metering.controlplane.tenant.repository.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TenantService {

    private final TenantRepository tenantRepository;

    public TenantService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    public Tenant registerTenant(String name) {
        if (tenantRepository.existsByNameIgnoreCase(name)) {
            throw new TenantAlreadyExistsException(name);
        }

        Tenant tenant = Tenant.builder()
                .name(name)
                .status(TenantStatus.ACTIVE)
                .build();

        return tenantRepository.save(tenant);
    }

    @Transactional(readOnly = true)
    public Tenant getTenant(UUID id) {
        return tenantRepository.findById(id).orElseThrow(() -> new TenantNotFoundException(id));
    }

    /** Loads the tenant with a pessimistic write lock; must be called inside a transaction. */
    public Tenant getTenantForUpdate(UUID id) {
        return tenantRepository.findByIdForUpdate(id).orElseThrow(() -> new TenantNotFoundException(id));
    }
}
