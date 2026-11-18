package com.saas.metering.controlplane.plan.repository;

import com.saas.metering.controlplane.plan.utils.BillingPeriod;
import com.saas.metering.controlplane.plan.entity.Plan;
import com.saas.metering.controlplane.plan.entity.RateLimitRule;
import com.saas.metering.controlplane.plan.utils.RuleAction;
import com.saas.metering.controlplane.plan.utils.RuleType;
import com.saas.metering.controlplane.ControlPlaneApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ContextConfiguration(classes = ControlPlaneApplication.class)
class PlanRepositoryTest {

    @Autowired
    private PlanRepository planRepository;

    private Plan plan(String code, boolean active) {
        Plan plan = Plan.builder().code(code).name(code).includedCalls(10).overagePricePaise(200).currency("INR")
                .billingPeriod(BillingPeriod.MONTHLY).active(active).build();
        plan.getRateLimitRules().add(RateLimitRule.builder().type(RuleType.REQUESTS_PER_WINDOW).windowSeconds(60)
                .callLimit(100L).action(RuleAction.THROTTLE).build());
        return plan;
    }

    @Test
    void savesPlanWithRules_andFindsByCode() {
        planRepository.saveAndFlush(plan("PRO", true));

        Plan found = planRepository.findByCode("PRO").orElseThrow();
        assertThat(found.getRateLimitRules()).hasSize(1);
        assertThat(found.getRateLimitRules().get(0).getCallLimit()).isEqualTo(100L);
        assertThat(planRepository.existsByCode("PRO")).isTrue();
        assertThat(planRepository.existsByCode("NOPE")).isFalse();
    }

    @Test
    void duplicateCode_violatesUniqueConstraint() {
        planRepository.saveAndFlush(plan("PRO", true));
        assertThatThrownBy(() -> planRepository.saveAndFlush(plan("PRO", true)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void filtersByActive() {
        planRepository.saveAndFlush(plan("A", true));
        planRepository.saveAndFlush(plan("B", false));
        assertThat(planRepository.findByActiveOrderByCodeAsc(true)).extracting(Plan::getCode).containsExactly("A");
        assertThat(planRepository.findAllByOrderByCodeAsc()).hasSize(2);
    }
}
