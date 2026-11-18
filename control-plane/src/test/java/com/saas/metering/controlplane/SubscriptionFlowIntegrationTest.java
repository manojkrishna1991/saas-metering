package com.saas.metering.controlplane;

import com.saas.metering.controlplane.plan.service.PlanService;
import com.saas.metering.controlplane.plan.utils.PlanSeedRunner;
import com.saas.metering.controlplane.plan.repository.PlanRepository;
import com.saas.metering.controlplane.subscription.repository.SubscriptionRepository;
import com.saas.metering.controlplane.subscription.utils.SubscriptionStatus;
import com.saas.metering.controlplane.tenant.entity.Tenant;
import com.saas.metering.controlplane.tenant.repository.TenantRepository;
import com.saas.metering.controlplane.tenant.utils.TenantStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end acceptance tests for Story 1.2 against H2. */
@SpringBootTest
@AutoConfigureMockMvc
class SubscriptionFlowIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private PlanRepository planRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private com.saas.metering.controlplane.plan.service.PlanService planService;

    @BeforeEach
    void clean() throws Exception {
        subscriptionRepository.deleteAll();
        planRepository.deleteAll();
        tenantRepository.deleteAll();
        new PlanSeedRunner(planRepository, planService).run(new DefaultApplicationArguments());
    }

    private UUID tenant(TenantStatus status) {
        return tenantRepository.save(Tenant.builder().name("T-" + UUID.randomUUID()).status(status).build()).getId();
    }

    private void assign(UUID tenantId, String code, int expectedStatus) throws Exception {
        mockMvc.perform(post("/v1/tenants/" + tenantId + "/subscription")
                        .contentType("application/json").content("{\"planCode\":\"" + code + "\"}"))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void seedIsIdempotent_andProHasExpectedTerms() throws Exception {
        new PlanSeedRunner(planRepository, planService).run(new DefaultApplicationArguments());

        assertThat(planRepository.count()).isEqualTo(2);
        mockMvc.perform(get("/v1/plans?active=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='PRO')].includedCalls").value(50000))
                .andExpect(jsonPath("$[?(@.code=='PRO')].overagePricePaise").value(200))
                .andExpect(jsonPath("$[?(@.code=='FREE')].overagePricePaise").value(0));
    }

    @Test
    void assignSwitchHistoryAndCancel() throws Exception {
        UUID tenantId = tenant(TenantStatus.ACTIVE);

        assign(tenantId, "PRO", 201);
        mockMvc.perform(get("/v1/tenants/" + tenantId + "/subscription"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.plan.code").value("PRO"))
                .andExpect(jsonPath("$.plan.rateLimitRules.length()").value(2));

        assign(tenantId, "PRO", 409);
        assign(tenantId, "FREE", 201);

        mockMvc.perform(get("/v1/tenants/" + tenantId + "/subscriptions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].plan.code").value("FREE"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[1].plan.code").value("PRO"))
                .andExpect(jsonPath("$[1].status").value("CANCELLED"))
                .andExpect(jsonPath("$[1].endDate").isNotEmpty());

        mockMvc.perform(delete("/v1/tenants/" + tenantId + "/subscription")).andExpect(status().isNoContent());
        mockMvc.perform(delete("/v1/tenants/" + tenantId + "/subscription")).andExpect(status().isNotFound());
        mockMvc.perform(get("/v1/tenants/" + tenantId + "/subscription")).andExpect(status().isNotFound());
    }

    @Test
    void notFoundAndConflictCases() throws Exception {
        assign(UUID.randomUUID(), "PRO", 404);

        UUID active = tenant(TenantStatus.ACTIVE);
        assign(active, "NOPE", 404);

        assign(tenant(TenantStatus.SUSPENDED), "PRO", 409);

        UUID proId = planRepository.findByCode("PRO").orElseThrow().getId();
        String deactivate = """
                {"name":"Pro Tier","includedCalls":50000,"overagePricePaise":200,"active":false,"rateLimitRules":[]}""";
        mockMvc.perform(put("/v1/plans/" + proId).contentType("application/json").content(deactivate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.code").value("PRO"));
        assign(active, "PRO", 409);
    }

    @Test
    void planCreationErrors() throws Exception {
        String body = """
                {"code":"FREE","name":"Dup","includedCalls":1,"overagePricePaise":0}""";
        mockMvc.perform(post("/v1/plans").contentType("application/json").content(body))
                .andExpect(status().isConflict());

        String capWithOverage = """
                {"code":"CAPPED","name":"Capped","includedCalls":1,"overagePricePaise":5,
                 "rateLimitRules":[{"type":"HARD_CAP","action":"BLOCK"}]}""";
        mockMvc.perform(post("/v1/plans").contentType("application/json").content(capWithOverage))
                .andExpect(status().isUnprocessableContent());

        mockMvc.perform(post("/v1/plans").contentType("application/json")
                        .content("{\"code\":\"X\",\"name\":\"n\",\"includedCalls\":-1,\"overagePricePaise\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void concurrentAssigns_neverLeaveTwoActiveSubscriptions() throws Exception {
        UUID tenantId = tenant(TenantStatus.ACTIVE);
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            String code = i % 2 == 0 ? "PRO" : "FREE";
            Callable<Integer> task = () -> {
                start.await();
                return mockMvc.perform(post("/v1/tenants/" + tenantId + "/subscription")
                                .contentType("application/json").content("{\"planCode\":\"" + code + "\"}"))
                        .andReturn().getResponse().getStatus();
            };
            results.add(pool.submit(task));
        }
        start.countDown();
        for (Future<Integer> f : results) {
            assertThat(f.get()).isIn(201, 409);
        }
        pool.shutdown();

        assertThat(subscriptionRepository.countByTenantIdAndStatus(tenantId, SubscriptionStatus.ACTIVE)).isEqualTo(1);
    }
}
