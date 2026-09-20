package com.snake.playerservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Snake Arena - Player Service API")
                        .version("1.0.0 / 2.0.0")
                        .description("""
                                Layanan Manajemen Profil Pemain dan Kelas Ular (Snake Classes).
                                
                                **Nama:** Valentinus Marvel  
                                **NIM:** 245150201111027  
                                **Mata Kuliah:** Arsitektur Berbasis Layanan (ABL)
                                
                                ### Fitur Versioning (Breaking Changes):
                                - **v1 (Legacy API)**: Menggunakan struktur data flat untuk snakeType dan speed.
                                - **v2 (Modern RPG)**: Menghapus snakeType dan speed, menggantikannya dengan nested object `classProfile` dengan skill cooldown & active modifiers.
                                """)
                        .contact(new Contact().name("Valentinus Marvel")));
    }

    @Bean
    public GroupedOpenApi v1Api() {
        return GroupedOpenApi.builder()
                .group("v1-legacy-api")
                .pathsToMatch("/api/v1/**")
                .build();
    }

    @Bean
    public GroupedOpenApi v2Api() {
        return GroupedOpenApi.builder()
                .group("v2-modern-api")
                .pathsToMatch("/api/v2/**")
                .build();
    }
}
