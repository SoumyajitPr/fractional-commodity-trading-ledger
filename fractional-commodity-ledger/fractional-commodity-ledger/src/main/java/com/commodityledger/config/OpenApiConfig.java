package com.commodityledger.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/** Swagger UI at /swagger-ui.html — use the "Authorize" button with trader/trader123 or admin/admin123. */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Fractional Commodity Trading Ledger API",
                version = "1.0.0",
                description = "Buy and sell fractional quantities of commodities, with a double-entry ledger "
                        + "that keeps wallets, holdings and trades consistent."),
        security = @SecurityRequirement(name = "basicAuth"))
@SecurityScheme(name = "basicAuth", type = SecuritySchemeType.HTTP, scheme = "basic")
public class OpenApiConfig {
}
