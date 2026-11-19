package com.example.demo.aggregation;

import com.example.demo.event.UsageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;

@Service
public class UsageAggregationService {

    private static final Logger log = LoggerFactory.getLogger(UsageAggregationService.class);

    private final ProcessedEventRepository processedEvents;
    private final TenantUsageAggregateRepository aggregates;
    private final Clock clock;

    public UsageAggregationService(ProcessedEventRepository processedEvents,
                                   TenantUsageAggregateRepository aggregates, Clock clock) {
        this.processedEvents = processedEvents;
        this.aggregates = aggregates;
        this.clock = clock;
    }

    /** Billing cycle (YYYY-MM, UTC) in which the usage actually occurred. */
    public static String billingCycle(Instant eventTimestamp) {
        return YearMonth.from(eventTimestamp.atZone(ZoneOffset.UTC)).toString();
    }

    /**
     * Records the event and bumps the aggregate in one transaction.
     * @return true if applied, false if the event was already processed.
     */
    @Transactional
    public boolean process(UsageEvent event) {
        if (processedEvents.existsById(event.eventId())) {
            log.debug("Event {} already processed, skipping", event.eventId());
            return false;
        }
        Instant now = clock.instant();
        // Primary-key violation here (concurrent duplicate) rolls the whole transaction back.
        processedEvents.saveAndFlush(new ProcessedEvent(event.eventId(), now));

        String cycle = billingCycle(event.eventTimestamp());
        int updated = aggregates.increment(event.tenantId(), event.metricType(), cycle, event.quantity(), now);
        if (updated == 0) {
            aggregates.saveAndFlush(new TenantUsageAggregate(event.tenantId(), event.metricType(), cycle, event.quantity(), now));
        }
        return true;
    }
}
