package com.saas.metering.controlplane.tenant.controller;

import com.saas.metering.controlplane.tenant.dto.CreateTenantRequest;
import com.saas.metering.controlplane.tenant.entity.Tenant;
import com.saas.metering.controlplane.tenant.exception.TenantAlreadyExistsException;
import com.saas.metering.controlplane.tenant.utils.TenantStatus;
import com.saas.metering.controlplane.tenant.service.TenantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TenantController.class)
class TenantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private TenantService tenantService;

    @Test
    void createTenant_returns201_withLocationAndBody() throws Exception {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        Tenant tenant = Tenant.builder()
                .id(id)
                .name("Globex Corp")
                .status(TenantStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build();
        when(tenantService.registerTenant("Globex Corp")).thenReturn(tenant);

        mockMvc.perform(post("/v1/tenants")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreateTenantRequest("Globex Corp"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/v1/tenants/" + id))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Globex Corp"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void createTenant_returns400_whenNameIsBlank() throws Exception {
        mockMvc.perform(post("/v1/tenants")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreateTenantRequest(" "))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTenant_returns409_whenTenantAlreadyExists() throws Exception {
        when(tenantService.registerTenant(anyString())).thenThrow(new TenantAlreadyExistsException("Globex Corp"));

        mockMvc.perform(post("/v1/tenants")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreateTenantRequest("Globex Corp"))))
                .andExpect(status().isConflict());
    }
}
