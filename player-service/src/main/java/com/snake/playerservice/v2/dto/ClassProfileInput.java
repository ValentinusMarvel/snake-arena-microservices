package com.snake.playerservice.v2.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ClassProfileInput(
        @NotBlank(message = "className wajib diisi (VIPER, PYTHON, COBRA)")
        @Pattern(regexp = "VIPER|PYTHON|COBRA", flags = Pattern.Flag.CASE_INSENSITIVE,
                message = "className harus salah satu dari VIPER, PYTHON, COBRA")
        @Schema(example = "VIPER", allowableValues = {"VIPER", "PYTHON", "COBRA"}) String className,

        @NotBlank(message = "passiveEffect wajib diisi")
        @Schema(example = "POISON_TRAIL") String passiveEffect,

        @NotNull(message = "activeAbility wajib diisi")
        @Valid AbilityInput activeAbility
) {}
