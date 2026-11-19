package com.example.demo.invoice;

import java.math.BigDecimal;

/** What a tenant's plan includes for one metric, plus the flat plan fee. */
public record PlanEntitlement(String metricType, long includedAllowance, BigDecimal overageRate,
                              BigDecimal basePlanFee, String currency) {
}
