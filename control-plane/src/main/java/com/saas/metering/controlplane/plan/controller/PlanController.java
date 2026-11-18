package com.saas.metering.controlplane.plan.controller;

import com.saas.metering.controlplane.plan.dto.CreatePlanRequest;
import com.saas.metering.controlplane.plan.entity.Plan;
import com.saas.metering.controlplane.plan.dto.PlanResponse;
import com.saas.metering.controlplane.plan.dto.UpdatePlanRequest;
import com.saas.metering.controlplane.plan.service.PlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import com.saas.metering.controlplane.plan.mapper.PlanMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Plans", description = "Billing plans and their rate-limit rules")
@RequestMapping("/v1/plans")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @Operation(summary = "Create a plan", responses = {
            @ApiResponse(responseCode = "201", description = "Plan created"),
            @ApiResponse(responseCode = "400", description = "Invalid payload"),
            @ApiResponse(responseCode = "409", description = "Plan code already exists"),
            @ApiResponse(responseCode = "422", description = "Contradictory rate-limit rules")
    })
    @PostMapping
    public ResponseEntity<PlanResponse> createPlan(@Valid @RequestBody CreatePlanRequest request) {
        Plan plan = planService.createPlan(request);
        return ResponseEntity.created(URI.create("/v1/plans/" + plan.getId())).body(PlanMapper.toResponse(plan));
    }

    @Operation(summary = "List plans", responses = {
            @ApiResponse(responseCode = "200", description = "Plans, optionally filtered by active")
    })
    @GetMapping
    public List<PlanResponse> listPlans(@RequestParam(required = false) Boolean active) {
        return planService.listPlans(active).stream().map(PlanMapper::toResponse).toList();
    }

    @Operation(summary = "Get a plan", responses = {
            @ApiResponse(responseCode = "200", description = "The plan"),
            @ApiResponse(responseCode = "404", description = "Plan not found")
    })
    @GetMapping("/{id}")
    public PlanResponse getPlan(@PathVariable UUID id) {
        return PlanMapper.toResponse(planService.getPlan(id));
    }

    @Operation(summary = "Update a plan (code is immutable; rules are replaced)", responses = {
            @ApiResponse(responseCode = "200", description = "Plan updated"),
            @ApiResponse(responseCode = "400", description = "Invalid payload"),
            @ApiResponse(responseCode = "404", description = "Plan not found"),
            @ApiResponse(responseCode = "422", description = "Contradictory rate-limit rules")
    })
    @PutMapping("/{id}")
    public PlanResponse updatePlan(@PathVariable UUID id, @Valid @RequestBody UpdatePlanRequest request) {
        return PlanMapper.toResponse(planService.updatePlan(id, request));
    }
}
