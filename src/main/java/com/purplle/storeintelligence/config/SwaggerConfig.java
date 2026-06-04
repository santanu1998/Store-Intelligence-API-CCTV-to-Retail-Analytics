package com.purplle.storeintelligence.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.*;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Purplle Store Intelligence API",
                version = "1.0.0",
                description = "Real-time retail analytics API. Ingests CCTV detection events and "
                        + "POS transactions to compute store conversion rate, zone heatmap, "
                        + "funnel analysis, and live anomaly detection.",
                contact = @Contact(name = "Purplle Tech", email = "purplletechchallenge2026@hackerearth.com")
        ),
        servers = @Server(url = "http://localhost:8080", description = "Local development")
)
public class SwaggerConfig {
    // All config is via annotations — Spring Boot + springdoc handles the rest.
}
