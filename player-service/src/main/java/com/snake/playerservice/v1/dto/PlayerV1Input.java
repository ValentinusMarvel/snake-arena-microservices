package com.snake.playerservice.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlayerV1Input(
        @NotBlank(message = "Username tidak boleh kosong")
        @Size(min = 3, max = 30, message = "Username harus antara 3 - 30 karakter")
        @Schema(example = "PlayerOne#1024", description = "Nama panggilan pemain dengan tag unik")
        String username,

        @NotBlank(message = "snakeType tidak boleh kosong")
        @Schema(example = "VIPER", description = "Tipe ular (VIPER, PYTHON, COBRA)")
        String snakeType
) {}
