package com.mossyanwar.ordersinvoicing.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI document at /v3/api-docs and Swagger UI at /swagger-ui.html. */
@Configuration
public class OpenApiConfig {

    private static final String BASIC_AUTH = "basicAuth";

    @Bean
    OpenAPI ordersInvoicingOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Orders and invoicing API")
                        .version("0.1.0")
                        .description("""
                                Customers, products, orders, invoices with UK VAT, credit notes and payments.
                                Errors are RFC 7807 problem details. POST /api/orders and POST /api/payments \
                                need an Idempotency-Key header."""))
                .components(new Components().addSecuritySchemes(BASIC_AUTH,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic")))
                .addSecurityItem(new SecurityRequirement().addList(BASIC_AUTH));
    }
}
