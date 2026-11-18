package com.saas.metering.controlplane.plan.controller;

import com.saas.metering.controlplane.plan.utils.BillingPeriod;
import com.saas.metering.controlplane.plan.exception.InvalidRateLimitRulesException;
import com.saas.metering.controlplane.plan.entity.Plan;
import com.saas.metering.controlplane.plan.exception.PlanAlreadyExistsException;
import com.saas.metering.controlplane.plan.exception.PlanNotFoundException;
import com.saas.metering.controlplane.plan.entity.RateLimitRule;
import com.saas.metering.controlplane.plan.utils.RuleAction;
import com.saas.metering.controlplane.plan.utils.RuleType;
import com.saas.metering.controlplane.plan.service.PlanService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PlanController.class)
class PlanControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private PlanService planService;

    private static final String PRO_JSON = """
            {"code":"PRO","name":"Pro Tier","includedCalls":50000,"overagePricePaise":200,
             "metricType": "API_CALLS",
             "currency":"INR","billingPeriod":"MONTHLY","active":true,
             "rateLimitRules":[{"type":"REQUESTS_PER_WINDOW","windowSeconds":60,"limit":1000,"action":"THROTTLE"}]}""";

    private Plan pro(UUID id) {
        Instant now = Instant.now();
        Plan plan = Plan.builder().id(id).code("PRO").name("Pro Tier").includedCalls(50_000).overagePricePaise(200)
                .currency("INR").billingPeriod(BillingPeriod.MONTHLY).active(true).createdAt(now).updatedAt(now).build();
        plan.getRateLimitRules().add(RateLimitRule.builder().id(UUID.randomUUID()).type(RuleType.REQUESTS_PER_WINDOW)
                .windowSeconds(60).callLimit(1000L).action(RuleAction.THROTTLE).build());
        return plan;
    }

    @Test
    void create_returns201_withLocationAndBody() throws Exception {
        UUID id = UUID.randomUUID();
        when(planService.createPlan(any())).thenReturn(pro(id));

        mockMvc.perform(post("/v1/plans").contentType("application/json").content(PRO_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/v1/plans/" + id))
                .andExpect(jsonPath("$.code").value("PRO"))
                .andExpect(jsonPath("$.includedCalls").value(50000))
                .andExpect(jsonPath("$.overagePricePaise").value(200))
                .andExpect(jsonPath("$.rateLimitRules[0].limit").value(1000))
                .andExpect(jsonPath("$.metricType").value("API_CALLS"));
    }

    @Test
    void create_returns400_onInvalidPayload() throws Exception {
        String negative = PRO_JSON.replace("50000", "-1");
        String badCode = PRO_JSON.replace("\"PRO\"", "\"pro tier\"");
        String badEnum = PRO_JSON.replace("THROTTLE", "EXPLODE");
        for (String body : List.of(negative, badCode, badEnum, "{}")) {
            mockMvc.perform(post("/v1/plans").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void create_returns409_onDuplicate() throws Exception {
        when(planService.createPlan(any())).thenThrow(new PlanAlreadyExistsException("PRO"));
        mockMvc.perform(post("/v1/plans").contentType("application/json").content(PRO_JSON))
                .andExpect(status().isConflict());
    }

    @Test
    void create_returns422_onContradictoryRules() throws Exception {
        when(planService.createPlan(any())).thenThrow(new InvalidRateLimitRulesException("bad rules"));
        mockMvc.perform(post("/v1/plans").contentType("application/json").content(PRO_JSON))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("bad rules"));
    }

    @Test
    void list_passesActiveFilter() throws Exception {
        when(planService.listPlans(true)).thenReturn(List.of(pro(UUID.randomUUID())));
        mockMvc.perform(get("/v1/plans?active=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("PRO"));
    }

    @Test
    void get_returns200_and404() throws Exception {
        UUID id = UUID.randomUUID();
        when(planService.getPlan(id)).thenReturn(pro(id));
        mockMvc.perform(get("/v1/plans/" + id)).andExpect(status().isOk());

        UUID missing = UUID.randomUUID();
        when(planService.getPlan(missing)).thenThrow(new PlanNotFoundException(missing.toString()));
        mockMvc.perform(get("/v1/plans/" + missing)).andExpect(status().isNotFound());
    }

    @Test
    void update_returns200_andValidates() throws Exception {
        UUID id = UUID.randomUUID();
        when(planService.updatePlan(any(), any())).thenReturn(pro(id));
        String body = """
                {"name":"Pro Tier","includedCalls":1,"overagePricePaise":2,"active":true,"rateLimitRules":[]}""";
        mockMvc.perform(put("/v1/plans/" + id).contentType("application/json").content(body))
                .andExpect(status().isOk());
        mockMvc.perform(put("/v1/plans/" + id).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
    }
}
