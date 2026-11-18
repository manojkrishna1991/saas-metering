package com.saas.metering.controlplane.subscription.repository;

import com.saas.metering.controlplane.subscription.entity.Subscription;
import com.saas.metering.controlplane.subscription.utils.SubscriptionStatus;
import com.saas.metering.controlplane.ControlPlaneApplication;
import com.saas.metering.controlplane.plan.utils.BillingPeriod;
import com.saas.metering.controlplane.plan.entity.Plan;
import com.saas.metering.controlplane.plan.repository.PlanRepository;
import com.saas.metering.controlplane.tenant.entity.Tenant;
import com.saas.metering.controlplane.tenant.repository.TenantRepository;
import com.saas.metering.controlplane.tenant.utils.TenantStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ContextConfiguration(classes = ControlPlaneApplication.class)
class SubscriptionRepositoryTest {

    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private PlanRepository planRepository;
    @Autowired private TenantRepository tenantRepository;

    @Test
    void findsActiveAndOrdersHistoryNewestFirst() {
        Tenant tenant = tenantRepository.save(Tenant.builder().name("Globex").status(TenantStatus.ACTIVE).build());
        Plan plan = planRepository.save(Plan.builder().code("PRO").name("Pro").includedCalls(1).overagePricePaise(1)
                .currency("INR").billingPeriod(BillingPeriod.MONTHLY).active(true).build());
        Instant t0 = Instant.now().minus(2, ChronoUnit.DAYS);
        subscriptionRepository.save(Subscription.builder().tenant(tenant).plan(plan)
                .status(SubscriptionStatus.CANCELLED).startDate(t0).endDate(t0.plusSeconds(60)).build());
        subscriptionRepository.save(Subscription.builder().tenant(tenant).plan(plan)
                .status(SubscriptionStatus.ACTIVE).startDate(t0.plusSeconds(3600)).build());
        subscriptionRepository.flush();

        assertThat(subscriptionRepository.findByTenantIdAndStatus(tenant.getId(), SubscriptionStatus.ACTIVE))
                .isPresent();
        assertThat(subscriptionRepository.countByTenantIdAndStatus(tenant.getId(), SubscriptionStatus.ACTIVE))
                .isEqualTo(1);
        assertThat(subscriptionRepository.findByTenantIdOrderByStartDateDescCreatedAtDesc(tenant.getId()))
                .extracting(Subscription::getStatus)
                .containsExactly(SubscriptionStatus.ACTIVE, SubscriptionStatus.CANCELLED);
    }
}
