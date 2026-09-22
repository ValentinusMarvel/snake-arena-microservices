package com.snake.playerservice.v2.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record PlayerV2Output(
        @Schema(example = "PL-101") String id,
        @Schema(example = "PlayerOne") String username,
        ClassProfileInput classProfile,
        @Schema(example = "150") int highScore,
        @Schema(example = "3") int totalMatches,
        @Schema(example = "Gold") String playerTier,
        @Schema(example = "2026-09-20T16:00:00") String createdAt
) {}
