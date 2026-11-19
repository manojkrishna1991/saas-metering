package com.example.demo.aggregation;

import com.example.demo.event.UsageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class UsageEventListener {

    private static final Logger log = LoggerFactory.getLogger(UsageEventListener.class);

    private final UsageAggregationService service;

    public UsageEventListener(UsageAggregationService service) {
        this.service = service;
    }

    @KafkaListener(topics = "${billing.kafka.topic:tenant-usage-events}", groupId = "${spring.kafka.consumer.group-id:billing-worker-group}")
    public void onEvent(UsageEvent event) {
        if (event == null) {
            // Undeserializable payloads are routed to the DLT by the error handler; null here means a tombstone.
            return;
        }
        try {
            service.process(event);
        } catch (DataIntegrityViolationException e) {
            // A concurrent consumer inserted the same eventId first: already counted, ack cleanly.
            log.info("Event {} concurrently processed, skipping", event.eventId());
        }
    }
}
