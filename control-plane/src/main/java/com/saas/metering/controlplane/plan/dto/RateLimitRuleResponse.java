package com.saas.metering.controlplane.plan.dto;

import com.saas.metering.controlplane.plan.utils.RuleAction;
import com.saas.metering.controlplane.plan.utils.RuleType;
import java.util.UUID;

public record RateLimitRuleResponse(
        UUID id,
        RuleType type,
        Integer windowSeconds,
        Long limit,
        Integer thresholdPercent,
        RuleAction action
) {
}
