package com.snake.playerservice.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;

public record PlayerStatsUpdateInput(
        @Min(value = 0, message = "Skor tidak boleh negatif")
        @Schema(example = "120", description = "Skor akhir yang diraih dalam pertandingan")
        int score,

        @Schema(example = "true", description = "Apakah pertandingan selesai")
        boolean matchCompleted
) {}
