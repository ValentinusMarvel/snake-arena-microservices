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
                                
                                **Tim Pengembang:**
                                - Valentinus Marvel (245150201111027)
                                - Muhammad Raditya Arsyad (245150207111043)
                                - Muhamad Fahmi Siddiq Aqsha (245150200111030)
                                - Dafid Rosydan Hanif (245150200111029)
                                
                                **Mata Kuliah:** Arsitektur Berbasis Layanan (ABL)
                                """)
                        .contact(new Contact().name("Tim Pengembang ABL")));
    }
}
