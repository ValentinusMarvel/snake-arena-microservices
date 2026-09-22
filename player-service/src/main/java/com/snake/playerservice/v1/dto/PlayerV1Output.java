package com.snake.playerservice.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record PlayerV1Output(
        @Schema(example = "PL-101") String id,
        @Schema(example = "PlayerOne") String username,
        @Schema(example = "VIPER") String snakeType,
        @Schema(example = "6") int speed,
        @Schema(example = "150") int highScore,
        @Schema(example = "3") int totalMatches,
        @Schema(example = "2026-09-20T16:00:00") String createdAt
) {}
