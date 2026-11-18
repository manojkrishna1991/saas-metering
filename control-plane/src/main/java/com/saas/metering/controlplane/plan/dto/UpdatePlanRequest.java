package com.saas.metering.controlplane.plan.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdatePlanRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull @PositiveOrZero Long includedCalls,
        @NotNull @PositiveOrZero Long overagePricePaise,
        @NotNull Boolean active,
        @NotNull @Valid List<@NotNull @Valid RateLimitRuleRequest> rateLimitRules
) {
}
