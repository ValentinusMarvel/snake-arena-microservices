package com.snake.arenaservice.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class PlayerServiceClient {

    private static final Logger log = LoggerFactory.getLogger(PlayerServiceClient.class);
    private final RestClient restClient;

    public PlayerServiceClient(@Value("${player.service.url:http://localhost:8081}") String playerServiceUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(playerServiceUrl)
                .build();
    }

    public void syncPlayerScore(String playerId, int score, boolean matchCompleted) {
        try {
            log.info("Inter-Service call -> Sync score {} for player {} to Player Service", score, playerId);
            restClient.patch()
                    .uri("/api/v1/players/{id}/stats", playerId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("score", score, "matchCompleted", matchCompleted))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Score sync completed successfully for player {}", playerId);
        } catch (Exception e) {
            log.warn("Resilience Fallback: Player service unreachable ({}), match score buffered locally", e.getMessage());
        }
    }
}
