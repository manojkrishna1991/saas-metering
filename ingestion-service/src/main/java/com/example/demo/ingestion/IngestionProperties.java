package com.example.demo.ingestion;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ingestion")
public record IngestionProperties(Kafka kafka, Idempotency idempotency, Quota quota) {

    public record Kafka(String topic, Duration publishTimeout) {
        public Kafka {
            topic = topic == null ? "tenant-usage-events" : topic;
            publishTimeout = publishTimeout == null ? Duration.ofSeconds(10) : publishTimeout;
        }
    }

    public record Idempotency(Duration ttl) {
        public Idempotency {
            ttl = ttl == null ? Duration.ofHours(24) : ttl;
        }
    }

    public record Quota(Duration counterTtl) {
        public Quota {
            counterTtl = counterTtl == null ? Duration.ofDays(45) : counterTtl;
        }
    }

    public IngestionProperties {
        quota = quota == null ? new Quota(null) : quota;
        kafka = kafka == null ? new Kafka(null, null) : kafka;
        idempotency = idempotency == null ? new Idempotency(null) : idempotency;
    }
}
