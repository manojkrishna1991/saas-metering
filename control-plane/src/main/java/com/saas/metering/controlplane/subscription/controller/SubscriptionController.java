package com.saas.metering.controlplane.subscription.controller;

import com.saas.metering.controlplane.subscription.dto.AssignPlanRequest;
import com.saas.metering.controlplane.subscription.entity.Subscription;
import com.saas.metering.controlplane.subscription.dto.SubscriptionResponse;
import com.saas.metering.controlplane.subscription.service.SubscriptionService;
import com.saas.metering.controlplane.tenant.entity.Tenant;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import com.saas.metering.controlplane.subscription.mapper.SubscriptionMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Subscriptions", description = "Assign plans to tenants and view subscription history")
@RequestMapping("/v1/tenants/{tenantId}")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @Operation(summary = "Assign a plan to a tenant, replacing any active subscription", responses = {
            @ApiResponse(responseCode = "201", description = "Subscription created"),
            @ApiResponse(responseCode = "400", description = "Invalid payload"),
            @ApiResponse(responseCode = "404", description = "Tenant or plan not found"),
            @ApiResponse(responseCode = "409", description = "Same plan already active, plan inactive, or tenant not ACTIVE")
    })
    @PostMapping("/subscription")
    public ResponseEntity<SubscriptionResponse> assignPlan(@PathVariable UUID tenantId,
                                                           @Valid @RequestBody AssignPlanRequest request) {
        Subscription subscription = subscriptionService.assignPlan(tenantId, request.planCode());
        return ResponseEntity.created(URI.create("/v1/tenants/" + tenantId + "/subscription"))
                .body(SubscriptionMapper.toResponse(subscription));
    }

    @Operation(summary = "Get the tenant's active subscription", responses = {
            @ApiResponse(responseCode = "200", description = "Active subscription with plan and rules"),
            @ApiResponse(responseCode = "404", description = "Tenant or active subscription not found")
    })
    @GetMapping("/subscription")
    public SubscriptionResponse getActiveSubscription(@PathVariable UUID tenantId) {
        return SubscriptionMapper.toResponse(subscriptionService.getActiveSubscription(tenantId));
    }

    @Operation(summary = "Get subscription history, newest first", responses = {
            @ApiResponse(responseCode = "200", description = "Subscriptions"),
            @ApiResponse(responseCode = "404", description = "Tenant not found")
    })
    @GetMapping("/subscriptions")
    public List<SubscriptionResponse> getHistory(@PathVariable UUID tenantId) {
        return subscriptionService.getHistory(tenantId).stream().map(SubscriptionMapper::toResponse).toList();
    }

    @Operation(summary = "Cancel the active subscription", responses = {
            @ApiResponse(responseCode = "204", description = "Cancelled"),
            @ApiResponse(responseCode = "404", description = "Tenant or active subscription not found")
    })
    @DeleteMapping("/subscription")
    public ResponseEntity<Void> cancel(@PathVariable UUID tenantId) {
        subscriptionService.cancel(tenantId);
        return ResponseEntity.noContent().build();
    }
}
