package com.example.demo.aggregation;

import com.example.demo.event.UsageEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class UsageAggregationServiceTest {

    @Autowired UsageAggregationService service;
    @Autowired TenantUsageAggregateRepository aggregates;
    @Autowired ProcessedEventRepository processed;

    private UsageEvent event(UUID id, String tenant, long qty, String ts) {
        return new UsageEvent(id, tenant, "api_calls", qty, Instant.parse(ts));
    }

    @Test
    void newEventUpdatesAggregateAndRecordsEventId() {
        UUID id = UUID.randomUUID();
        assertTrue(service.process(event(id, "t-new", 7, "2026-11-05T10:00:00Z")));

        assertEquals(7, aggregates.findByTenantIdAndMetricTypeAndBillingCycle("t-new", "api_calls", "2026-11").orElseThrow().getTotalConsumed());
        assertTrue(processed.existsById(id));
    }

    @Test
    void redeliveredEventIsNotDoubleCounted() {
        UUID id = UUID.randomUUID();
        UsageEvent e = event(id, "t-dup", 5, "2026-11-05T10:00:00Z");
        assertTrue(service.process(e));
        assertFalse(service.process(e));

        assertEquals(5, aggregates.findByTenantIdAndMetricTypeAndBillingCycle("t-dup", "api_calls", "2026-11").orElseThrow().getTotalConsumed());
    }

    @Test
    void differentEventsAccumulate() {
        service.process(event(UUID.randomUUID(), "t-acc", 5, "2026-11-05T10:00:00Z"));
        service.process(event(UUID.randomUUID(), "t-acc", 3, "2026-11-20T10:00:00Z"));

        assertEquals(8, aggregates.findByTenantIdAndMetricTypeAndBillingCycle("t-acc", "api_calls", "2026-11").orElseThrow().getTotalConsumed());
    }

    @Test
    void priorMonthEventLandsInPriorCycle() {
        service.process(event(UUID.randomUUID(), "t-late", 4, "2026-09-30T23:59:59Z"));

        assertEquals(4, aggregates.findByTenantIdAndMetricTypeAndBillingCycle("t-late", "api_calls", "2026-09").orElseThrow().getTotalConsumed());
        assertTrue(aggregates.findByTenantIdAndMetricTypeAndBillingCycle("t-late", "api_calls", "2026-11").isEmpty());
    }
}
