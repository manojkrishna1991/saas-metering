package com.saas.metering.controlplane.quota;

import com.saas.metering.controlplane.plan.entity.Plan;
import com.saas.metering.controlplane.plan.utils.RuleType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Keeps {@code quota:limit:{tenantId}:{metricType}} in Redis in step with the tenant's active plan,
 * which the ingestion-service reads to enforce hard limits. Runs after the DB commit; a Redis outage
 * is logged and leaves the previous value in place.
 */
@Component
@ConditionalOnProperty(name = "app.quota-cache.enabled", havingValue = "true", matchIfMissing = true)
public class QuotaCacheSync {

    private static final Logger log = LoggerFactory.getLogger(QuotaCacheSync.class);

    private final StringRedisTemplate redis;

    public QuotaCacheSync(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSubscriptionChanged(SubscriptionChangedEvent event) {
        try {
            if (event.plan() == null) {
                // Cancelled: remove limits for every metric the platform meters.
                for (var metric : com.saas.metering.controlplane.plan.utils.MetricType.values()) {
                    redis.delete(limitKey(event.tenantId().toString(), metric.name()));
                }
                return;
            }
            Plan plan = event.plan();
            String key = limitKey(event.tenantId().toString(), plan.getMetricType().name());
            boolean hardCapped = plan.getRateLimitRules().stream().anyMatch(r -> r.getType() == RuleType.HARD_CAP);
            if (hardCapped) {
                redis.opsForValue().set(key, Long.toString(plan.getIncludedCalls()));
            } else {
                redis.delete(key); // no hard cap: unlimited, overage-billed
            }
            log.info("Quota limit for tenant {} metric {} -> {}", event.tenantId(), plan.getMetricType(),
                    hardCapped ? plan.getIncludedCalls() : "unlimited");
        } catch (RuntimeException e) {
            log.error("Failed to sync quota limit for tenant {}", event.tenantId(), e);
        }
    }

    static String limitKey(String tenantId, String metricType) {
        return "quota:limit:" + tenantId + ":" + metricType.toLowerCase();
    }
}
