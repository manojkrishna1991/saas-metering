package com.saas.metering.controlplane.plan.utils;

import com.saas.metering.controlplane.plan.dto.CreatePlanRequest;
import com.saas.metering.controlplane.plan.dto.RateLimitRuleRequest;
import com.saas.metering.controlplane.plan.repository.PlanRepository;
import com.saas.metering.controlplane.plan.service.PlanService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/** Seeds the Free and Pro plans on startup; idempotent. */
@Component
@ConditionalOnProperty(name = "app.seed-plans", havingValue = "true", matchIfMissing = true)
public class PlanSeedRunner implements ApplicationRunner {

    private final PlanRepository planRepository;
    private final PlanService planService;

    public PlanSeedRunner(PlanRepository planRepository, PlanService planService) {
        this.planRepository = planRepository;
        this.planService = planService;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed(new CreatePlanRequest("FREE", "Free Tier", 1_000L, 0L, "INR", BillingPeriod.MONTHLY, MetricType.API_CALLS, true, List.of(
                new RateLimitRuleRequest(RuleType.REQUESTS_PER_WINDOW, 60, 60L, null, RuleAction.THROTTLE),
                new RateLimitRuleRequest(RuleType.USAGE_PERCENT_ALERT, null, null, 80, RuleAction.NOTIFY),
                new RateLimitRuleRequest(RuleType.HARD_CAP, null, null, null, RuleAction.BLOCK))));
        seed(new CreatePlanRequest("PRO", "Pro Tier", 50_000L, 200L, "INR", BillingPeriod.MONTHLY,MetricType.API_CALLS, true, List.of(
                new RateLimitRuleRequest(RuleType.REQUESTS_PER_WINDOW, 60, 1000L, null, RuleAction.THROTTLE),
                new RateLimitRuleRequest(RuleType.USAGE_PERCENT_ALERT, null, null, 80, RuleAction.NOTIFY))));
    }

    private void seed(CreatePlanRequest request) {
        if (!planRepository.existsByCode(request.code())) {
            planService.createPlan(request);
        }
    }
}
