package com.snake.arenaservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record EatAppleInput(
        @NotBlank(message = "appleId wajib diisi")
        @Schema(example = "APL-1") String appleId
) {}
