package com.saas.metering.controlplane;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocsTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiDocs_listAllV1Paths() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("SaaS Metering Engine: Control Plane API"))
                .andExpect(jsonPath("$.paths['/v1/tenants'].post").exists())
                .andExpect(jsonPath("$.paths['/v1/plans'].post").exists())
                .andExpect(jsonPath("$.paths['/v1/plans'].get").exists())
                .andExpect(jsonPath("$.paths['/v1/plans/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/v1/plans/{id}'].put").exists())
                .andExpect(jsonPath("$.paths['/v1/tenants/{tenantId}/subscription'].post").exists())
                .andExpect(jsonPath("$.paths['/v1/tenants/{tenantId}/subscription'].get").exists())
                .andExpect(jsonPath("$.paths['/v1/tenants/{tenantId}/subscription'].delete").exists())
                .andExpect(jsonPath("$.paths['/v1/tenants/{tenantId}/subscriptions'].get").exists());
    }

    @Test
    void swaggerUi_isServed() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
