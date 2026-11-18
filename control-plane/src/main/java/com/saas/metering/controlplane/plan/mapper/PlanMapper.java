package com.saas.metering.controlplane.plan.mapper;

import com.saas.metering.controlplane.plan.utils.BillingPeriod;
import com.saas.metering.controlplane.plan.entity.Plan;
import com.saas.metering.controlplane.plan.entity.RateLimitRule;
import com.saas.metering.controlplane.plan.dto.CreatePlanRequest;
import com.saas.metering.controlplane.plan.dto.PlanResponse;
import com.saas.metering.controlplane.plan.dto.RateLimitRuleRequest;
import com.saas.metering.controlplane.plan.dto.RateLimitRuleResponse;

import java.util.List;

public final class PlanMapper {

    private PlanMapper() {
    }

    public static Plan toEntity(CreatePlanRequest request, List<RateLimitRuleRequest> rules) {
        Plan plan = Plan.builder()
                .code(request.code())
                .name(request.name())
                .includedCalls(request.includedCalls())
                .overagePricePaise(request.overagePricePaise())
                .currency("INR")
                .billingPeriod(BillingPeriod.MONTHLY)
                .metricType(request.metricType())
                .active(request.active() == null || request.active())
                .build();
        plan.getRateLimitRules().addAll(toRuleEntities(rules));
        return plan;
    }

    public static List<RateLimitRule> toRuleEntities(List<RateLimitRuleRequest> rules) {
        return rules.stream()
                .map(r -> RateLimitRule.builder()
                        .type(r.type())
                        .windowSeconds(r.windowSeconds())
                        .callLimit(r.limit())
                        .thresholdPercent(r.thresholdPercent())
                        .action(r.action())
                        .build())
                .toList();
    }

    public static RateLimitRuleResponse toResponse(RateLimitRule rule) {
        return new RateLimitRuleResponse(rule.getId(), rule.getType(), rule.getWindowSeconds(),
                rule.getCallLimit(), rule.getThresholdPercent(), rule.getAction());
    }

    public static PlanResponse toResponse(Plan plan) {
        return new PlanResponse(plan.getId(), plan.getCode(), plan.getName(), plan.getIncludedCalls(),
                plan.getOveragePricePaise(), plan.getCurrency(), plan.getBillingPeriod(), plan.isActive(),
                plan.getRateLimitRules().stream().map(PlanMapper::toResponse).toList(),
                plan.getCreatedAt(), plan.getUpdatedAt());
    }
}
