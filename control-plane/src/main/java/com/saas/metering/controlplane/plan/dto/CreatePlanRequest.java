package com.saas.metering.controlplane.plan.dto;

import com.saas.metering.controlplane.plan.utils.BillingPeriod;
import com.saas.metering.controlplane.plan.utils.MetricType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreatePlanRequest(
        @NotBlank @Pattern(regexp = "[A-Z0-9_]{2,32}") String code,
        @NotBlank @Size(max = 255) String name,
        @NotNull @PositiveOrZero Long includedCalls,
        @NotNull @PositiveOrZero Long overagePricePaise,
        @Pattern(regexp = "INR", message = "must be INR") String currency,
        BillingPeriod billingPeriod,
        MetricType metricType,
        Boolean active,
        @Valid List<@NotNull @Valid RateLimitRuleRequest> rateLimitRules
) {
}
