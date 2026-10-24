package com.salary.management.infrastructure.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI metadata for the Salary Management API. */
@Configuration
public class OpenApiConfig {

    /**
     * Defines the API title, version, and description shown in Swagger UI.
     *
     * @return OpenAPI metadata bean
     */
    @Bean
    public OpenAPI salaryManagementOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Salary Management API")
                        .version("1.0.0")
                        .description("Payroll data management and reporting for ACME's HR team."));
    }
}