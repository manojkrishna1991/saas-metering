package com.saas.metering.controlplane.plan.dto;

import com.saas.metering.controlplane.plan.utils.RuleAction;
import com.saas.metering.controlplane.plan.utils.RuleType;
import jakarta.validation.constraints.NotNull;

public record RateLimitRuleRequest(
        @NotNull RuleType type,
        Integer windowSeconds,
        Long limit,
        Integer thresholdPercent,
        @NotNull RuleAction action
) {
}
