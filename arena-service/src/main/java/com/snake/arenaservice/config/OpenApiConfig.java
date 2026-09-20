package com.snake.arenaservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Snake Arena - Gameplay & Session Service API")
                        .version("1.0.0")
                        .description("""
                                Layanan Arena Gameplay Session, Makanan (Apel), Tabrakan, Leaderboard, dan Inter-service Communication.
                                
                                **Nama:** Valentinus Marvel  
                                **NIM:** 245150201111027  
                                **Mata Kuliah:** Arsitektur Berbasis Layanan (ABL)
                                """)
                        .contact(new Contact().name("Valentinus Marvel")));
    }
}
