package com.saas.metering.controlplane.tenant.service;

import com.saas.metering.controlplane.tenant.entity.Tenant;
import com.saas.metering.controlplane.tenant.exception.TenantAlreadyExistsException;
import com.saas.metering.controlplane.tenant.utils.TenantStatus;
import com.saas.metering.controlplane.tenant.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    private TenantService tenantService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        tenantService = new TenantService(tenantRepository);
    }

    @Test
    void registerTenant_savesActiveTenant_whenNameIsNew() {
        when(tenantRepository.existsByNameIgnoreCase("Globex Corp")).thenReturn(false);
        when(tenantRepository.save(org.mockito.ArgumentMatchers.any(Tenant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Tenant result = tenantService.registerTenant("Globex Corp");

        ArgumentCaptor<Tenant> captor = ArgumentCaptor.forClass(Tenant.class);
        org.mockito.Mockito.verify(tenantRepository).save(captor.capture());

        assertThat(captor.getValue().getName()).isEqualTo("Globex Corp");
        assertThat(captor.getValue().getStatus()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(result.getStatus()).isEqualTo(TenantStatus.ACTIVE);
    }

    @Test
    void registerTenant_throwsConflict_whenNameAlreadyExists() {
        when(tenantRepository.existsByNameIgnoreCase(anyString())).thenReturn(true);

        assertThatThrownBy(() -> tenantService.registerTenant("Globex Corp"))
                .isInstanceOf(TenantAlreadyExistsException.class)
                .hasMessageContaining("Globex Corp");

        org.mockito.Mockito.verify(tenantRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }
}
