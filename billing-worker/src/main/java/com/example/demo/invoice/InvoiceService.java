package com.example.demo.invoice;

import com.example.demo.aggregation.TenantUsageAggregate;
import com.example.demo.aggregation.TenantUsageAggregateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class InvoiceService {

    private final TenantUsageAggregateRepository aggregates;
    private final PlanEntitlementProvider entitlements;
    private final InvoiceRepository invoices;
    private final Clock clock;

    public InvoiceService(TenantUsageAggregateRepository aggregates, PlanEntitlementProvider entitlements,
                          InvoiceRepository invoices, Clock clock) {
        this.aggregates = aggregates;
        this.entitlements = entitlements;
        this.invoices = invoices;
        this.clock = clock;
    }

    /**
     * Rates the tenant's aggregated usage for the cycle and persists the invoice with its line items
     * in one transaction. Idempotent: an existing invoice for the same tenant and cycle is returned.
     */
    @Transactional
    public Invoice generate(String tenantId, String billingCycle) {
        return invoices.findByTenantIdAndBillingCycle(tenantId, billingCycle)
                .orElseGet(() -> create(tenantId, billingCycle));
    }

    private Invoice create(String tenantId, String billingCycle) {
        List<PlanEntitlement> plan = entitlements.findEntitlements(tenantId, billingCycle);
        if (plan.isEmpty()) {
            throw new NoActivePlanException(tenantId, billingCycle);
        }
        Map<String, Long> usage = aggregates.findByTenantIdAndBillingCycle(tenantId, billingCycle).stream()
                .collect(Collectors.toMap(TenantUsageAggregate::getMetricType, TenantUsageAggregate::getTotalConsumed, Long::sum));

        BigDecimal baseFee = plan.get(0).basePlanFee().setScale(RatingCalculator.CURRENCY_SCALE, RoundingMode.HALF_UP);
        String currency = plan.get(0).currency();

        List<InvoiceLineItem> lines = new ArrayList<>();
        BigDecimal total = baseFee;
        for (PlanEntitlement e : plan) {
            long consumed = usage.getOrDefault(e.metricType(), 0L);
            long billable = RatingCalculator.billableUnits(consumed, e.includedAllowance());
            BigDecimal amount = RatingCalculator.metricCharge(billable, e.overageRate());
            lines.add(new InvoiceLineItem(e.metricType(), consumed, e.includedAllowance(), billable, amount));
            total = total.add(amount);
        }
        Invoice invoice = new Invoice(tenantId, billingCycle, currency, baseFee, total, clock.instant());
        lines.forEach(invoice::addLineItem);
        return invoices.save(invoice);
    }
}
