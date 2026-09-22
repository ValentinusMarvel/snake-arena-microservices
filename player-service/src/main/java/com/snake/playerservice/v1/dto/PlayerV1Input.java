package com.snake.playerservice.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlayerV1Input(
        @NotBlank(message = "Username tidak boleh kosong")
        @Size(min = 3, max = 20, message = "Username harus antara 3 - 20 karakter")
        @Schema(example = "PlayerOne", description = "Nama panggilan pemain")
        String username,

        @NotBlank(message = "snakeType tidak boleh kosong")
        @Schema(example = "VIPER", description = "Tipe ular (VIPER, PYTHON, COBRA)")
        String snakeType
) {}
