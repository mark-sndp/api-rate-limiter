package com.ratelimiter.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes API metadata for the springdoc-generated OpenAPI description and Swagger UI.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI rateLimiterOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("API Rate Limiter")
                        .description("Per-client API rate limiting service: admin policy management and demo protected endpoint")
                        .version("0.1.0"));
    }
}
