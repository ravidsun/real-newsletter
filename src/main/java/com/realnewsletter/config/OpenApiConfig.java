package com.realnewsletter.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger UI configuration.
 * UI is available at <a href="http://localhost:8080/swagger-ui.html">/swagger-ui.html</a>.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Real Newsletter API")
                        .description("AI-powered news aggregation and delivery platform")
                        .version("1.5.0"));
    }
}

