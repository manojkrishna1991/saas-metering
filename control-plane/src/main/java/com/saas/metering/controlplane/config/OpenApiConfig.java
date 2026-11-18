package com.saas.metering.controlplane.config;

import com.saas.metering.controlplane.tenant.entity.Tenant;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI controlPlaneOpenApi() {
        return new OpenAPI().info(new Info()
                .title("SaaS Metering Engine: Control Plane API")
                .version("v1")
                .description("Tenant onboarding, billing plans and subscription assignment."));
    }
}
