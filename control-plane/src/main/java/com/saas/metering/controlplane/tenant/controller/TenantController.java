package com.saas.metering.controlplane.tenant.controller;

import com.saas.metering.controlplane.tenant.dto.CreateTenantRequest;
import com.saas.metering.controlplane.tenant.entity.Tenant;
import com.saas.metering.controlplane.tenant.dto.TenantResponse;
import com.saas.metering.controlplane.tenant.service.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import com.saas.metering.controlplane.tenant.mapper.TenantMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@Tag(name = "Tenants", description = "Tenant onboarding")
@RequestMapping("/v1/tenants")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @Operation(summary = "Register a tenant", responses = {
            @ApiResponse(responseCode = "201", description = "Tenant created"),
            @ApiResponse(responseCode = "400", description = "Invalid payload"),
            @ApiResponse(responseCode = "409", description = "Tenant name already exists")
    })
    @PostMapping
    public ResponseEntity<TenantResponse> createTenant(@Valid @RequestBody CreateTenantRequest request) {
        Tenant tenant = tenantService.registerTenant(request.name());
        TenantResponse response = TenantMapper.toResponse(tenant);
        return ResponseEntity.created(URI.create("/v1/tenants/" + tenant.getId())).body(response);
    }
}
