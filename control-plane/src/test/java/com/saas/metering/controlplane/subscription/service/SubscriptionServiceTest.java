package com.saas.metering.controlplane.subscription.service;

import com.saas.metering.controlplane.subscription.entity.Subscription;
import com.saas.metering.controlplane.subscription.exception.SubscriptionConflictException;
import com.saas.metering.controlplane.subscription.exception.SubscriptionNotFoundException;
import com.saas.metering.controlplane.subscription.utils.SubscriptionStatus;
import com.saas.metering.controlplane.subscription.repository.SubscriptionRepository;
import com.saas.metering.controlplane.plan.entity.Plan;
import com.saas.metering.controlplane.plan.exception.PlanNotFoundException;
import com.saas.metering.controlplane.plan.service.PlanService;
import com.saas.metering.controlplane.tenant.entity.Tenant;
import com.saas.metering.controlplane.tenant.exception.TenantNotFoundException;
import com.saas.metering.controlplane.tenant.service.TenantService;
import com.saas.metering.controlplane.tenant.utils.TenantStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private PlanService planService;
    @Mock private TenantService tenantService;

    private SubscriptionService service;
    private final UUID tenantId = UUID.randomUUID();
    private Tenant tenant;
    private Plan free;
    private Plan pro;

    @BeforeEach
    void setUp() {
        service = new SubscriptionService(subscriptionRepository, planService, tenantService);
        tenant = Tenant.builder().id(tenantId).name("Globex").status(TenantStatus.ACTIVE).build();
        free = Plan.builder().id(UUID.randomUUID()).code("FREE").active(true).build();
        pro = Plan.builder().id(UUID.randomUUID()).code("PRO").active(true).build();
    }

    private Subscription active(Plan plan) {
        return Subscription.builder().id(UUID.randomUUID()).tenant(tenant).plan(plan)
                .status(SubscriptionStatus.ACTIVE).build();
    }

    @Test
    void assignPlan_createsActiveSubscription_whenTenantHasNone() {
        when(tenantService.getTenantForUpdate(tenantId)).thenReturn(tenant);
        when(planService.getPlanByCode("PRO")).thenReturn(pro);
        when(subscriptionRepository.findByTenantIdAndStatus(tenantId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(i -> i.getArgument(0));

        Subscription result = service.assignPlan(tenantId, "PRO");

        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(result.getPlan()).isSameAs(pro);
        assertThat(result.getStartDate()).isNotNull();
        assertThat(result.getEndDate()).isNull();
    }

    @Test
    void assignPlan_switchesPlan_closingCurrentSubscription() {
        Subscription current = active(pro);
        when(tenantService.getTenantForUpdate(tenantId)).thenReturn(tenant);
        when(planService.getPlanByCode("FREE")).thenReturn(free);
        when(subscriptionRepository.findByTenantIdAndStatus(tenantId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(current));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(i -> i.getArgument(0));

        Subscription result = service.assignPlan(tenantId, "FREE");

        assertThat(current.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(current.getEndDate()).isNotNull();
        assertThat(result.getPlan()).isSameAs(free);
        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void assignPlan_conflict_whenSamePlanAlreadyActive() {
        when(tenantService.getTenantForUpdate(tenantId)).thenReturn(tenant);
        when(planService.getPlanByCode("PRO")).thenReturn(pro);
        when(subscriptionRepository.findByTenantIdAndStatus(tenantId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(active(pro)));

        assertThatThrownBy(() -> service.assignPlan(tenantId, "PRO"))
                .isInstanceOf(SubscriptionConflictException.class).hasMessageContaining("already on plan");
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void assignPlan_conflict_whenPlanInactive() {
        pro.setActive(false);
        when(tenantService.getTenantForUpdate(tenantId)).thenReturn(tenant);
        when(planService.getPlanByCode("PRO")).thenReturn(pro);

        assertThatThrownBy(() -> service.assignPlan(tenantId, "PRO"))
                .isInstanceOf(SubscriptionConflictException.class).hasMessageContaining("inactive");
    }

    @Test
    void assignPlan_conflict_whenTenantNotActive() {
        tenant.setStatus(TenantStatus.SUSPENDED);
        when(tenantService.getTenantForUpdate(tenantId)).thenReturn(tenant);

        assertThatThrownBy(() -> service.assignPlan(tenantId, "PRO"))
                .isInstanceOf(SubscriptionConflictException.class).hasMessageContaining("SUSPENDED");
    }

    @Test
    void assignPlan_propagatesNotFound() {
        when(tenantService.getTenantForUpdate(tenantId)).thenThrow(new TenantNotFoundException(tenantId));
        assertThatThrownBy(() -> service.assignPlan(tenantId, "PRO")).isInstanceOf(TenantNotFoundException.class);

        doReturn(tenant).when(tenantService).getTenantForUpdate(tenantId);
        when(planService.getPlanByCode("NOPE")).thenThrow(new PlanNotFoundException("NOPE"));
        assertThatThrownBy(() -> service.assignPlan(tenantId, "NOPE")).isInstanceOf(PlanNotFoundException.class);
    }

    @Test
    void getActiveSubscription_notFound_whenNone() {
        when(subscriptionRepository.findByTenantIdAndStatus(tenantId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getActiveSubscription(tenantId))
                .isInstanceOf(SubscriptionNotFoundException.class);
    }

    @Test
    void cancel_closesActiveSubscription() {
        Subscription current = active(pro);
        when(subscriptionRepository.findByTenantIdAndStatus(tenantId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(current));

        service.cancel(tenantId);

        assertThat(current.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(current.getEndDate()).isNotNull();
    }

    @Test
    void cancel_notFound_whenNoActiveSubscription() {
        when(subscriptionRepository.findByTenantIdAndStatus(tenantId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.cancel(tenantId)).isInstanceOf(SubscriptionNotFoundException.class);
    }
}
