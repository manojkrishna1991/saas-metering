package com.saas.metering.controlplane.subscription.controller;

import com.saas.metering.controlplane.subscription.entity.Subscription;
import com.saas.metering.controlplane.subscription.exception.SubscriptionConflictException;
import com.saas.metering.controlplane.subscription.exception.SubscriptionNotFoundException;
import com.saas.metering.controlplane.subscription.utils.SubscriptionStatus;
import com.saas.metering.controlplane.subscription.service.SubscriptionService;
import com.saas.metering.controlplane.plan.utils.BillingPeriod;
import com.saas.metering.controlplane.plan.entity.Plan;
import com.saas.metering.controlplane.tenant.entity.Tenant;
import com.saas.metering.controlplane.tenant.exception.TenantNotFoundException;
import com.saas.metering.controlplane.tenant.utils.TenantStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SubscriptionController.class)
class SubscriptionControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private SubscriptionService subscriptionService;

    private final UUID tenantId = UUID.randomUUID();
    private final String assign = "{\"planCode\":\"PRO\"}";

    private Subscription sub(SubscriptionStatus status) {
        Instant now = Instant.now();
        Tenant tenant = Tenant.builder().id(tenantId).name("Globex").status(TenantStatus.ACTIVE).build();
        Plan plan = Plan.builder().id(UUID.randomUUID()).code("PRO").name("Pro Tier").includedCalls(50_000)
                .overagePricePaise(200).currency("INR").billingPeriod(BillingPeriod.MONTHLY).active(true)
                .createdAt(now).updatedAt(now).build();
        return Subscription.builder().id(UUID.randomUUID()).tenant(tenant).plan(plan).status(status)
                .startDate(now).build();
    }

    @Test
    void assign_returns201_withLocationAndEmbeddedPlan() throws Exception {
        when(subscriptionService.assignPlan(tenantId, "PRO")).thenReturn(sub(SubscriptionStatus.ACTIVE));

        mockMvc.perform(post("/v1/tenants/" + tenantId + "/subscription")
                        .contentType("application/json").content(assign))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/v1/tenants/" + tenantId + "/subscription"))
                .andExpect(jsonPath("$.tenantId").value(tenantId.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.plan.code").value("PRO"))
                .andExpect(jsonPath("$.plan.overagePricePaise").value(200));
    }

    @Test
    void assign_returns400_whenPlanCodeBlank() throws Exception {
        mockMvc.perform(post("/v1/tenants/" + tenantId + "/subscription")
                        .contentType("application/json").content("{\"planCode\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void assign_returns409_and404() throws Exception {
        when(subscriptionService.assignPlan(tenantId, "PRO"))
                .thenThrow(new SubscriptionConflictException("already on plan"));
        mockMvc.perform(post("/v1/tenants/" + tenantId + "/subscription")
                        .contentType("application/json").content(assign))
                .andExpect(status().isConflict());

        doThrow(new TenantNotFoundException(tenantId)).when(subscriptionService).assignPlan(tenantId, "PRO");
        mockMvc.perform(post("/v1/tenants/" + tenantId + "/subscription")
                        .contentType("application/json").content(assign))
                .andExpect(status().isNotFound());
    }

    @Test
    void getActive_returns200_and404() throws Exception {
        when(subscriptionService.getActiveSubscription(tenantId)).thenReturn(sub(SubscriptionStatus.ACTIVE));
        mockMvc.perform(get("/v1/tenants/" + tenantId + "/subscription")).andExpect(status().isOk());

        UUID other = UUID.randomUUID();
        when(subscriptionService.getActiveSubscription(other)).thenThrow(new SubscriptionNotFoundException(other));
        mockMvc.perform(get("/v1/tenants/" + other + "/subscription")).andExpect(status().isNotFound());
    }

    @Test
    void history_returnsList() throws Exception {
        when(subscriptionService.getHistory(tenantId))
                .thenReturn(List.of(sub(SubscriptionStatus.ACTIVE), sub(SubscriptionStatus.CANCELLED)));
        mockMvc.perform(get("/v1/tenants/" + tenantId + "/subscriptions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    void cancel_returns204_and404() throws Exception {
        mockMvc.perform(delete("/v1/tenants/" + tenantId + "/subscription")).andExpect(status().isNoContent());

        doThrow(new SubscriptionNotFoundException(tenantId)).when(subscriptionService).cancel(tenantId);
        mockMvc.perform(delete("/v1/tenants/" + tenantId + "/subscription")).andExpect(status().isNotFound());
    }
}
