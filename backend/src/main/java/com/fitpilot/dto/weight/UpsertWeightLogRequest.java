package com.fitpilot.dto.weight;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpsertWeightLogRequest(
        @NotNull
        @DecimalMin("30.0")
        @DecimalMax("350.0")
        BigDecimal weightKg
) {
}
