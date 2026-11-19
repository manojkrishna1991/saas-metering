package com.example.demo.aggregation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant_usage_aggregates",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "metric_type", "billing_cycle"}))
@Getter
@Setter
@NoArgsConstructor
public class TenantUsageAggregate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "metric_type", nullable = false, length = 64)
    private String metricType;

    /** Billing cycle in YYYY-MM (UTC). */
    @Column(name = "billing_cycle", nullable = false, length = 7)
    private String billingCycle;

    @Column(name = "total_consumed", nullable = false)
    private long totalConsumed;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public TenantUsageAggregate(String tenantId, String metricType, String billingCycle, long totalConsumed, Instant updatedAt) {
        this.tenantId = tenantId;
        this.metricType = metricType;
        this.billingCycle = billingCycle;
        this.totalConsumed = totalConsumed;
        this.updatedAt = updatedAt;
    }
}
