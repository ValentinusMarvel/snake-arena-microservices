package com.snake.arenaservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ArenaServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ArenaServiceApplication.class, args);
    }
}
