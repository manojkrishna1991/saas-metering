package com.example.demo.event;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record UsageEvent(
        @NotNull(message = "eventId is required for idempotency")
        UUID eventId,

        @NotBlank(message = "tenantId is required")
        String tenantId,

        @NotBlank(message = "metricType is required (e.g., api_calls, ai_tokens, storage_gb)")
        String metricType,

        @NotNull @Min(value = 1, message = "quantity must be at least 1")
        Long quantity,

        @NotNull(message = "eventTimestamp is required")
        Instant eventTimestamp
) {}
