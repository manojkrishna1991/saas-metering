package com.example.demo.aggregation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantUsageAggregateRepository extends JpaRepository<TenantUsageAggregate, UUID> {

    Optional<TenantUsageAggregate> findByTenantIdAndMetricTypeAndBillingCycle(String tenantId, String metricType, String billingCycle);

    List<TenantUsageAggregate> findByTenantIdAndBillingCycle(String tenantId, String billingCycle);

    /** Atomic in-database increment; returns the number of rows updated (0 if the aggregate does not exist yet). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update TenantUsageAggregate a
               set a.totalConsumed = a.totalConsumed + :quantity, a.updatedAt = :now
             where a.tenantId = :tenantId and a.metricType = :metricType and a.billingCycle = :billingCycle""")
    int increment(@Param("tenantId") String tenantId, @Param("metricType") String metricType,
                  @Param("billingCycle") String billingCycle, @Param("quantity") long quantity,
                  @Param("now") Instant now);
}
