package com.snake.playerservice.v2.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record AbilityInput(
        @NotBlank(message = "Nama ability wajib diisi")
        @Schema(example = "Venom Dash") String name,

        @Positive(message = "Cooldown harus lebih dari 0")
        @Schema(example = "5") int cooldownSeconds,

        @Positive(message = "Speed multiplier harus positif")
        @Schema(example = "1.8") double speedMultiplier
) {}
