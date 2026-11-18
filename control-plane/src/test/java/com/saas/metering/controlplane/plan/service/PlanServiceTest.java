package com.saas.metering.controlplane.plan.service;

import com.saas.metering.controlplane.plan.utils.BillingPeriod;
import com.saas.metering.controlplane.plan.dto.CreatePlanRequest;
import com.saas.metering.controlplane.plan.exception.InvalidRateLimitRulesException;
import com.saas.metering.controlplane.plan.entity.Plan;
import com.saas.metering.controlplane.plan.exception.PlanAlreadyExistsException;
import com.saas.metering.controlplane.plan.exception.PlanNotFoundException;
import com.saas.metering.controlplane.plan.entity.RateLimitRule;
import com.saas.metering.controlplane.plan.dto.RateLimitRuleRequest;
import com.saas.metering.controlplane.plan.utils.RuleAction;
import com.saas.metering.controlplane.plan.utils.RuleType;
import com.saas.metering.controlplane.plan.dto.UpdatePlanRequest;
import com.saas.metering.controlplane.plan.repository.PlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanServiceTest {

    @Mock
    private PlanRepository planRepository;

    private PlanService planService;

    @BeforeEach
    void setUp() {
        planService = new PlanService(planRepository);
    }

    private static CreatePlanRequest req(long overage, RateLimitRuleRequest... rules) {
        return new CreatePlanRequest("PRO", "Pro Tier", 50_000L, overage, null, null, null,null, List.of(rules));
    }

    private static final RateLimitRuleRequest BURST =
            new RateLimitRuleRequest(RuleType.REQUESTS_PER_WINDOW, 60, 1000L, null, RuleAction.THROTTLE);
    private static final RateLimitRuleRequest ALERT_80 =
            new RateLimitRuleRequest(RuleType.USAGE_PERCENT_ALERT, null, null, 80, RuleAction.NOTIFY);
    private static final RateLimitRuleRequest CAP =
            new RateLimitRuleRequest(RuleType.HARD_CAP, null, null, null, RuleAction.BLOCK);

    @Test
    void createPlan_savesProPlanWithDefaults() {
        when(planRepository.existsByCode("PRO")).thenReturn(false);
        when(planRepository.save(any(Plan.class))).thenAnswer(i -> i.getArgument(0));

        Plan plan = planService.createPlan(req(200, BURST, ALERT_80));

        assertThat(plan.getIncludedCalls()).isEqualTo(50_000);
        assertThat(plan.getOveragePricePaise()).isEqualTo(200);
        assertThat(plan.getCurrency()).isEqualTo("INR");
        assertThat(plan.getBillingPeriod()).isEqualTo(BillingPeriod.MONTHLY);
        assertThat(plan.isActive()).isTrue();
        assertThat(plan.getRateLimitRules()).hasSize(2);
    }

    @Test
    void createPlan_rejectsDuplicateCode() {
        when(planRepository.existsByCode("PRO")).thenReturn(true);

        assertThatThrownBy(() -> planService.createPlan(req(200)))
                .isInstanceOf(PlanAlreadyExistsException.class);
        verify(planRepository, never()).save(any());
    }

    @Test
    void createPlan_rejectsTwoHardCaps() {
        when(planRepository.existsByCode("PRO")).thenReturn(false);
        assertThatThrownBy(() -> planService.createPlan(req(0, CAP, CAP)))
                .isInstanceOf(InvalidRateLimitRulesException.class).hasMessageContaining("at most one");
    }

    @Test
    void createPlan_rejectsDuplicateAlertThresholds() {
        when(planRepository.existsByCode("PRO")).thenReturn(false);
        assertThatThrownBy(() -> planService.createPlan(req(200, ALERT_80, ALERT_80)))
                .isInstanceOf(InvalidRateLimitRulesException.class).hasMessageContaining("Duplicate");
    }

    @Test
    void createPlan_rejectsHardCapWithOverage() {
        when(planRepository.existsByCode("PRO")).thenReturn(false);
        assertThatThrownBy(() -> planService.createPlan(req(200, CAP)))
                .isInstanceOf(InvalidRateLimitRulesException.class).hasMessageContaining("overagePricePaise = 0");
    }

    @Test
    void createPlan_rejectsBurstRuleWithoutWindow() {
        when(planRepository.existsByCode("PRO")).thenReturn(false);
        var bad = new RateLimitRuleRequest(RuleType.REQUESTS_PER_WINDOW, null, 10L, null, RuleAction.THROTTLE);
        assertThatThrownBy(() -> planService.createPlan(req(200, bad)))
                .isInstanceOf(InvalidRateLimitRulesException.class).hasMessageContaining("windowSeconds");
    }

    @Test
    void createPlan_rejectsMismatchedFieldsAndActions() {
        when(planRepository.existsByCode("PRO")).thenReturn(false);
        var alertWithWindow = new RateLimitRuleRequest(RuleType.USAGE_PERCENT_ALERT, 60, null, 80, RuleAction.NOTIFY);
        var alertWrongAction = new RateLimitRuleRequest(RuleType.USAGE_PERCENT_ALERT, null, null, 80, RuleAction.BLOCK);
        var capWithThreshold = new RateLimitRuleRequest(RuleType.HARD_CAP, null, null, 50, RuleAction.BLOCK);
        var alertOutOfRange = new RateLimitRuleRequest(RuleType.USAGE_PERCENT_ALERT, null, null, 101, RuleAction.NOTIFY);
        for (var bad : List.of(alertWithWindow, alertWrongAction, capWithThreshold, alertOutOfRange)) {
            assertThatThrownBy(() -> planService.createPlan(req(0, bad)))
                    .isInstanceOf(InvalidRateLimitRulesException.class);
        }
    }

    @Test
    void getPlan_throwsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(planRepository.findById(id)).thenReturn(java.util.Optional.empty());
        assertThatThrownBy(() -> planService.getPlan(id)).isInstanceOf(PlanNotFoundException.class);
    }

    @Test
    void updatePlan_replacesRulesAndKeepsCode() {
        UUID id = UUID.randomUUID();
        Plan existing = Plan.builder().id(id).code("PRO").name("Old").includedCalls(1).overagePricePaise(1)
                .currency("INR").billingPeriod(BillingPeriod.MONTHLY).active(true).build();
        existing.getRateLimitRules().add(RateLimitRule.builder().type(RuleType.HARD_CAP).action(RuleAction.BLOCK).build());
        when(planRepository.findById(id)).thenReturn(java.util.Optional.of(existing));
        when(planRepository.save(any(Plan.class))).thenAnswer(i -> i.getArgument(0));

        Plan updated = planService.updatePlan(id, new UpdatePlanRequest("New", 100L, 5L, false, List.of(ALERT_80)));

        assertThat(updated.getCode()).isEqualTo("PRO");
        assertThat(updated.getName()).isEqualTo("New");
        assertThat(updated.isActive()).isFalse();
        assertThat(updated.getRateLimitRules()).extracting(RateLimitRule::getType)
                .containsExactly(RuleType.USAGE_PERCENT_ALERT);
    }

    @Test
    void updatePlan_validatesRules() {
        UUID id = UUID.randomUUID();
        Plan existing = Plan.builder().id(id).code("PRO").build();
        when(planRepository.findById(id)).thenReturn(java.util.Optional.of(existing));
        assertThatThrownBy(() -> planService.updatePlan(id, new UpdatePlanRequest("n", 1L, 5L, true, List.of(CAP))))
                .isInstanceOf(InvalidRateLimitRulesException.class);
    }
}
