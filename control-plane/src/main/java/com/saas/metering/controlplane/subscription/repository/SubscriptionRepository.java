package com.saas.metering.controlplane.subscription.repository;

import com.saas.metering.controlplane.subscription.entity.Subscription;
import com.saas.metering.controlplane.subscription.utils.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByTenantIdAndStatus(UUID tenantId, SubscriptionStatus status);

    List<Subscription> findByTenantIdOrderByStartDateDescCreatedAtDesc(UUID tenantId);

    long countByTenantIdAndStatus(UUID tenantId, SubscriptionStatus status);
}
