package com.snake.arenaservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LoadoutUpdateInput(
        @NotBlank(message = "snakeType wajib diisi")
        @Schema(example = "COBRA") String snakeType,

        @Schema(example = "#ffd700") String color
) {}
