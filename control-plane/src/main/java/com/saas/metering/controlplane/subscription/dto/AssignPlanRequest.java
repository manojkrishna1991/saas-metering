package com.saas.metering.controlplane.subscription.dto;

import jakarta.validation.constraints.NotBlank;

public record AssignPlanRequest(@NotBlank String planCode) {
}
