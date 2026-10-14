package com.fitpilot.dto.training;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record WorkoutSetRequest(
        @NotBlank @Size(max = 100) String exerciseName,
        @NotNull @Min(1) @Max(30) Integer setNumber,
        @NotNull @DecimalMin("0.0") @DecimalMax("1000.0") @Digits(integer = 4, fraction = 2)
        BigDecimal weightKg,
        @NotNull @Min(1) @Max(100) Integer reps,
        @NotNull @Min(0) @Max(10) Integer rir
) {
}
