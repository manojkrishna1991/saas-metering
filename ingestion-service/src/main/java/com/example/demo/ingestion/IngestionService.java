package com.example.demo.ingestion;

import com.example.demo.event.UsageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZoneOffset;
import java.time.YearMonth;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);
    static final String KEY_PREFIX = "idempotent:event:";
    static final String USAGE_PREFIX = "quota:usage:";
    static final String LIMIT_PREFIX = "quota:limit:";

    private final StringRedisTemplate redis;
    private final KafkaTemplate<String, UsageEvent> kafka;
    private final IngestionProperties props;

    public IngestionService(StringRedisTemplate redis, KafkaTemplate<String, UsageEvent> kafka,
                            IngestionProperties props) {
        this.redis = redis;
        this.kafka = kafka;
        this.props = props;
    }

    /**
     * Order: idempotency check, atomic quota check and increment, Kafka publish.
     * Duplicates are silently ignored. A quota breach (429) or a publish failure (503)
     * releases the idempotency key (and the quota reservation) so a client retry can succeed.
     */
    public void ingest(UsageEvent event) {
        String key = KEY_PREFIX + event.eventId();
        Boolean first = redis.opsForValue().setIfAbsent(key, "1", props.idempotency().ttl());
        if (!Boolean.TRUE.equals(first)) {
            log.debug("Duplicate event {} ignored", event.eventId());
            return;
        }
        String usageKey = reserveQuota(event, key);
        try {
            kafka.send(props.kafka().topic(), event.tenantId(), event)
                    .get(props.kafka().publishTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            rollback(key, usageKey, event, e);
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            rollback(key, usageKey, event, e);
        }
    }

    /** Increments the usage counter; rejects with 429 if the hard limit would be exceeded. */
    private String reserveQuota(UsageEvent event, String idempotencyKey) {
        String cycle = YearMonth.from(event.eventTimestamp().atZone(ZoneOffset.UTC)).toString();
        String usageKey = USAGE_PREFIX + event.tenantId() + ":" + event.metricType() + ":" + cycle;
        Long newTotal = redis.opsForValue().increment(usageKey, event.quantity());
        redis.expire(usageKey, props.quota().counterTtl());

        String limit = redis.opsForValue().get(LIMIT_PREFIX + event.tenantId() + ":" + event.metricType());
        if (limit != null && newTotal != null && newTotal > Long.parseLong(limit.trim())) {
            redis.opsForValue().decrement(usageKey, event.quantity());
            redis.delete(idempotencyKey);
            log.warn("Quota exceeded for tenant {} metric {}", event.tenantId(), event.metricType());
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Quota exceeded for metric");
        }
        return usageKey;
    }

    private void rollback(String key, String usageKey, UsageEvent event, Exception cause) {
        log.error("Kafka publish failed for event {} (tenant {}); releasing idempotency key and quota",
                event.eventId(), event.tenantId(), cause);
        try {
            redis.opsForValue().decrement(usageKey, event.quantity());
            redis.delete(key);
        } catch (RuntimeException e) {
            log.error("Failed to roll back Redis state for event {}", event.eventId(), e);
        }
        throw new EventPublishException("Unable to publish event " + event.eventId(), cause);
    }
}
