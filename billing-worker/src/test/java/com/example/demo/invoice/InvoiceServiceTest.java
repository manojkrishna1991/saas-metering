package com.example.demo.invoice;

import com.example.demo.aggregation.TenantUsageAggregate;
import com.example.demo.aggregation.TenantUsageAggregateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    @Mock TenantUsageAggregateRepository aggregates;
    @Mock PlanEntitlementProvider entitlements;
    @Mock InvoiceRepository invoices;

    private InvoiceService service() {
        when(invoices.findByTenantIdAndBillingCycle("t", "2026-11")).thenReturn(java.util.Optional.empty());
        lenient().when(invoices.save(any(Invoice.class))).thenAnswer(i -> i.getArgument(0));
        return new InvoiceService(aggregates, entitlements, invoices, Clock.systemUTC());
    }

    private static TenantUsageAggregate usage(String metric, long total) {
        return new TenantUsageAggregate("t", metric, "2026-11", total, Instant.now());
    }

    @Test
    void usageAtOrBelowAllowanceHasZeroCharge() {
        InvoiceService s = service();
        when(entitlements.findEntitlements("t", "2026-11")).thenReturn(List.of(
                new PlanEntitlement("api_calls", 1000, new BigDecimal("2.000000"), BigDecimal.ZERO, "INR")));
        when(aggregates.findByTenantIdAndBillingCycle("t", "2026-11")).thenReturn(List.of(usage("api_calls", 1000)));

        Invoice invoice = s.generate("t", "2026-11");

        assertEquals(new BigDecimal("0.00"), invoice.getLineItems().get(0).getLineAmount());
        assertEquals(0, invoice.getLineItems().get(0).getBillableUnits());
        assertEquals(new BigDecimal("0.00"), invoice.getTotalAmount());
    }

    @Test
    void overageIsExactDecimal() {
        InvoiceService s = service();
        when(entitlements.findEntitlements("t", "2026-11")).thenReturn(List.of(
                new PlanEntitlement("ai_tokens", 100, new BigDecimal("0.000123"), BigDecimal.ZERO, "INR")));
        when(aggregates.findByTenantIdAndBillingCycle("t", "2026-11")).thenReturn(List.of(usage("ai_tokens", 1_000_100)));

        Invoice invoice = s.generate("t", "2026-11");

        // 1,000,000 * 0.000123 = 123.00 exactly
        assertEquals(new BigDecimal("123.00"), invoice.getLineItems().get(0).getLineAmount());
    }

    @Test
    void multipleMetricsAreSummedWithBaseFee() {
        InvoiceService s = service();
        BigDecimal base = new BigDecimal("499.00");
        when(entitlements.findEntitlements("t", "2026-11")).thenReturn(List.of(
                new PlanEntitlement("api_calls", 50_000, new BigDecimal("2.000000"), base, "INR"),
                new PlanEntitlement("ai_tokens", 1_000, new BigDecimal("0.001500"), base, "INR"),
                new PlanEntitlement("storage_gb", 10, new BigDecimal("12.345678"), base, "INR")));
        when(aggregates.findByTenantIdAndBillingCycle("t", "2026-11")).thenReturn(List.of(
                usage("api_calls", 50_010),   // 10 * 2        = 20.00
                usage("ai_tokens", 3_000),    // 2000 * 0.0015 = 3.00
                usage("storage_gb", 13)));    // 3 * 12.345678 = 37.037034 -> 37.04

        Invoice invoice = s.generate("t", "2026-11");

        assertEquals(3, invoice.getLineItems().size());
        assertEquals(new BigDecimal("37.04"), invoice.getLineItems().get(2).getLineAmount());
        assertEquals(new BigDecimal("559.04"), invoice.getTotalAmount());
    }

    @Test
    void noPlanThrows() {
        InvoiceService s = service();
        when(entitlements.findEntitlements("t", "2026-11")).thenReturn(List.of());
        assertThrows(NoActivePlanException.class, () -> s.generate("t", "2026-11"));
    }
}
