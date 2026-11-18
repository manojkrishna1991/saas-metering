package com.saas.metering.controlplane.plan.service;

import com.saas.metering.controlplane.plan.dto.CreatePlanRequest;
import com.saas.metering.controlplane.plan.exception.InvalidRateLimitRulesException;
import com.saas.metering.controlplane.plan.entity.Plan;
import com.saas.metering.controlplane.plan.exception.PlanAlreadyExistsException;
import com.saas.metering.controlplane.plan.exception.PlanNotFoundException;
import com.saas.metering.controlplane.plan.dto.RateLimitRuleRequest;
import com.saas.metering.controlplane.plan.utils.RuleAction;
import com.saas.metering.controlplane.plan.dto.UpdatePlanRequest;
import com.saas.metering.controlplane.plan.repository.PlanRepository;
import com.saas.metering.controlplane.plan.mapper.PlanMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class PlanService {

    private final PlanRepository planRepository;

    public PlanService(PlanRepository planRepository) {
        this.planRepository = planRepository;
    }

    @Transactional
    public Plan createPlan(CreatePlanRequest request) {
        if (planRepository.existsByCode(request.code())) {
            throw new PlanAlreadyExistsException(request.code());
        }
        List<RateLimitRuleRequest> rules = request.rateLimitRules() == null ? List.of() : request.rateLimitRules();
        validateRules(rules, request.overagePricePaise());

        return planRepository.save(PlanMapper.toEntity(request, rules));
    }

    @Transactional(readOnly = true)
    public List<Plan> listPlans(Boolean active) {
        return active == null ? planRepository.findAllByOrderByCodeAsc()
                : planRepository.findByActiveOrderByCodeAsc(active);
    }

    @Transactional(readOnly = true)
    public Plan getPlan(UUID id) {
        return planRepository.findById(id).orElseThrow(() -> new PlanNotFoundException(id.toString()));
    }

    @Transactional(readOnly = true)
    public Plan getPlanByCode(String code) {
        return planRepository.findByCode(code).orElseThrow(() -> new PlanNotFoundException(code));
    }

    @Transactional
    public Plan updatePlan(UUID id, UpdatePlanRequest request) {
        Plan plan = getPlan(id);
        validateRules(request.rateLimitRules(), request.overagePricePaise());

        plan.setName(request.name());
        plan.setIncludedCalls(request.includedCalls());
        plan.setOveragePricePaise(request.overagePricePaise());
        plan.setActive(request.active());
        plan.getRateLimitRules().clear();
        plan.getRateLimitRules().addAll(PlanMapper.toRuleEntities(request.rateLimitRules()));
        return planRepository.save(plan);
    }

    private void validateRules(List<RateLimitRuleRequest> rules, long overagePricePaise) {
        int hardCaps = 0;
        Set<Integer> thresholds = new HashSet<>();
        for (RateLimitRuleRequest rule : rules) {
            switch (rule.type()) {
                case REQUESTS_PER_WINDOW -> {
                    require(rule.windowSeconds() != null && rule.windowSeconds() > 0,
                            "REQUESTS_PER_WINDOW requires windowSeconds > 0");
                    require(rule.limit() != null && rule.limit() > 0, "REQUESTS_PER_WINDOW requires limit > 0");
                    require(rule.thresholdPercent() == null, "REQUESTS_PER_WINDOW must not set thresholdPercent");
                    require(rule.action() == RuleAction.THROTTLE || rule.action() == RuleAction.BLOCK,
                            "REQUESTS_PER_WINDOW action must be THROTTLE or BLOCK");
                }
                case USAGE_PERCENT_ALERT -> {
                    require(rule.thresholdPercent() != null && rule.thresholdPercent() >= 1
                            && rule.thresholdPercent() <= 100, "USAGE_PERCENT_ALERT requires thresholdPercent 1..100");
                    require(rule.windowSeconds() == null && rule.limit() == null,
                            "USAGE_PERCENT_ALERT must not set windowSeconds or limit");
                    require(rule.action() == RuleAction.NOTIFY, "USAGE_PERCENT_ALERT action must be NOTIFY");
                    require(thresholds.add(rule.thresholdPercent()),
                            "Duplicate USAGE_PERCENT_ALERT threshold " + rule.thresholdPercent());
                }
                case HARD_CAP -> {
                    require(rule.windowSeconds() == null && rule.limit() == null && rule.thresholdPercent() == null,
                            "HARD_CAP must not set windowSeconds, limit or thresholdPercent");
                    require(rule.action() == RuleAction.BLOCK, "HARD_CAP action must be BLOCK");
                    hardCaps++;
                }
            }
        }
        require(hardCaps <= 1, "A plan can have at most one HARD_CAP rule");
        require(hardCaps == 0 || overagePricePaise == 0, "A plan with a HARD_CAP must have overagePricePaise = 0");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new InvalidRateLimitRulesException(message);
        }
    }
}
