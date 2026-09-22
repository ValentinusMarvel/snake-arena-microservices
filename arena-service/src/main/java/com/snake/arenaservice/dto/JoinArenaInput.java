package com.snake.arenaservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record JoinArenaInput(
        @NotBlank(message = "playerId wajib diisi")
        @Schema(example = "PL-101") String playerId,

        @NotBlank(message = "playerName wajib diisi")
        @Schema(example = "PlayerOne") String playerName,

        @NotBlank(message = "snakeType wajib diisi (VIPER, PYTHON, COBRA)")
        @Schema(example = "VIPER") String snakeType,

        @Schema(example = "#00ff88") String color
) {}
