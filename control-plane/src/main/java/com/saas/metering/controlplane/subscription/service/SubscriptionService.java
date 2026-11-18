package com.saas.metering.controlplane.subscription.service;

import com.saas.metering.controlplane.subscription.entity.Subscription;
import com.saas.metering.controlplane.subscription.exception.SubscriptionConflictException;
import com.saas.metering.controlplane.subscription.exception.SubscriptionNotFoundException;
import com.saas.metering.controlplane.subscription.utils.SubscriptionStatus;
import com.saas.metering.controlplane.subscription.repository.SubscriptionRepository;
import com.saas.metering.controlplane.plan.entity.Plan;
import com.saas.metering.controlplane.plan.service.PlanService;
import com.saas.metering.controlplane.tenant.entity.Tenant;
import com.saas.metering.controlplane.tenant.service.TenantService;
import com.saas.metering.controlplane.tenant.utils.TenantStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final PlanService planService;
    private final TenantService tenantService;

    public SubscriptionService(SubscriptionRepository subscriptionRepository, PlanService planService,
                               TenantService tenantService) {
        this.subscriptionRepository = subscriptionRepository;
        this.planService = planService;
        this.tenantService = tenantService;
    }

    /** Assigns a plan, atomically replacing any active subscription. Serialised per tenant by a row lock. */
    @Transactional
    public Subscription assignPlan(UUID tenantId, String planCode) {
        Tenant tenant = tenantService.getTenantForUpdate(tenantId);
        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            throw new SubscriptionConflictException(
                    "Tenant '%s' is %s and cannot be assigned a plan".formatted(tenantId, tenant.getStatus()));
        }
        Plan plan = planService.getPlanByCode(planCode);
        if (!plan.isActive()) {
            throw new SubscriptionConflictException("Plan '%s' is inactive".formatted(planCode));
        }

        Instant now = Instant.now();
        subscriptionRepository.findByTenantIdAndStatus(tenantId, SubscriptionStatus.ACTIVE).ifPresent(current -> {
            if (current.getPlan().getId().equals(plan.getId())) {
                throw new SubscriptionConflictException(
                        "Tenant '%s' is already on plan '%s'".formatted(tenantId, planCode));
            }
            current.setStatus(SubscriptionStatus.CANCELLED);
            current.setEndDate(now);
            subscriptionRepository.saveAndFlush(current);
        });

        return subscriptionRepository.save(Subscription.builder()
                .tenant(tenant)
                .plan(plan)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(now)
                .build());
    }

    @Transactional(readOnly = true)
    public Subscription getActiveSubscription(UUID tenantId) {
        tenantService.getTenant(tenantId);
        return subscriptionRepository.findByTenantIdAndStatus(tenantId, SubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new SubscriptionNotFoundException(tenantId));
    }

    @Transactional(readOnly = true)
    public List<Subscription> getHistory(UUID tenantId) {
        tenantService.getTenant(tenantId);
        return subscriptionRepository.findByTenantIdOrderByStartDateDescCreatedAtDesc(tenantId);
    }

    @Transactional
    public void cancel(UUID tenantId) {
        tenantService.getTenantForUpdate(tenantId);
        Subscription current = subscriptionRepository.findByTenantIdAndStatus(tenantId, SubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new SubscriptionNotFoundException(tenantId));
        current.setStatus(SubscriptionStatus.CANCELLED);
        current.setEndDate(Instant.now());
        subscriptionRepository.save(current);
    }
}
