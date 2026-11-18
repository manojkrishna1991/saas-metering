package com.saas.metering.controlplane.plan.entity;

import com.saas.metering.controlplane.plan.utils.RuleAction;
import com.saas.metering.controlplane.plan.utils.RuleType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "rate_limit_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RateLimitRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RuleType type;

    private Integer windowSeconds;

    @Column(name = "call_limit")
    private Long callLimit;

    private Integer thresholdPercent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RuleAction action;
}
