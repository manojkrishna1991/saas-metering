package com.saas.metering.controlplane.tenant.repository;

import com.saas.metering.controlplane.ControlPlaneApplication;
import com.saas.metering.controlplane.tenant.entity.Tenant;
import com.saas.metering.controlplane.tenant.utils.TenantStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ContextConfiguration;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ContextConfiguration(classes = com.saas.metering.controlplane.ControlPlaneApplication.class)
class TenantRepositoryTest {

    @Autowired
    private TenantRepository tenantRepository;

    @Test
    void existsByNameIgnoreCase_isCaseInsensitive() {
        tenantRepository.save(Tenant.builder().name("Globex Corp").status(TenantStatus.ACTIVE).build());

        assertThat(tenantRepository.existsByNameIgnoreCase("globex corp")).isTrue();
        assertThat(tenantRepository.existsByNameIgnoreCase("Acme Inc")).isFalse();
    }

    @Test
    void findByNameIgnoreCase_returnsMatch() {
        Tenant saved = tenantRepository.save(Tenant.builder().name("Globex Corp").status(TenantStatus.ACTIVE).build());

        Optional<Tenant> found = tenantRepository.findByNameIgnoreCase("GLOBEX CORP");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    void savingDuplicateName_violatesUniqueConstraint() {
        tenantRepository.saveAndFlush(Tenant.builder().name("Globex Corp").status(TenantStatus.ACTIVE).build());

        assertThatThrownBy(() ->
                tenantRepository.saveAndFlush(Tenant.builder().name("Globex Corp").status(TenantStatus.ACTIVE).build())
        ).isInstanceOf(DataIntegrityViolationException.class);
    }
}
