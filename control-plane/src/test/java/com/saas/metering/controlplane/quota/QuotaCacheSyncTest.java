package com.saas.metering.controlplane.quota;

import com.saas.metering.controlplane.plan.entity.Plan;
import com.saas.metering.controlplane.plan.entity.RateLimitRule;
import com.saas.metering.controlplane.plan.utils.MetricType;
import com.saas.metering.controlplane.plan.utils.RuleAction;
import com.saas.metering.controlplane.plan.utils.RuleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuotaCacheSyncTest {

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> ops;

    QuotaCacheSync sync;
    UUID tenant = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        sync = new QuotaCacheSync(redis);
    }

    private Plan plan(long included, RuleType... rules) {
        return Plan.builder().metricType(MetricType.API_CALLS).includedCalls(included)
                .rateLimitRules(java.util.Arrays.stream(rules)
                        .map(t -> RateLimitRule.builder().type(t).action(RuleAction.BLOCK).build()).toList())
                .build();
    }

    @Test
    void hardCapPlanWritesLimit() {
        when(redis.opsForValue()).thenReturn(ops);
        sync.onSubscriptionChanged(new SubscriptionChangedEvent(tenant, plan(1000, RuleType.HARD_CAP)));
        verify(ops).set("quota:limit:" + tenant + ":api_calls", "1000");
    }

    @Test
    void planWithoutHardCapRemovesLimit() {
        sync.onSubscriptionChanged(new SubscriptionChangedEvent(tenant, plan(50_000, RuleType.USAGE_PERCENT_ALERT)));
        verify(redis).delete("quota:limit:" + tenant + ":api_calls");
    }

    @Test
    void cancellationRemovesLimits() {
        sync.onSubscriptionChanged(new SubscriptionChangedEvent(tenant, null));
        verify(redis).delete("quota:limit:" + tenant + ":api_calls");
        verify(redis).delete("quota:limit:" + tenant + ":ai_tokens");
        verify(redis).delete("quota:limit:" + tenant + ":storage_gb");
    }

    @Test
    void redisFailureDoesNotPropagate() {
        when(redis.delete(anyString())).thenThrow(new RuntimeException("redis down"));
        sync.onSubscriptionChanged(new SubscriptionChangedEvent(tenant, plan(10)));
    }
}
