package com.snake.playerservice.v2.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PlayerV2Input(
        @NotBlank(message = "Username tidak boleh kosong")
        @Size(min = 3, max = 30, message = "Username harus antara 3 - 30 karakter")
        @Schema(example = "PlayerOne") String username,

        @NotNull(message = "classProfile wajib ada pada API v2 (Breaking Change)")
        @Valid ClassProfileInput classProfile
) {}
