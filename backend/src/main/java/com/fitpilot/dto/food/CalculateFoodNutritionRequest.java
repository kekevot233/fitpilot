package com.fitpilot.dto.food;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CalculateFoodNutritionRequest(
        @NotNull
        @Positive(message = "食物 ID 必须为正整数。")
        @Schema(description = "食物 ID", example = "1")
        Long foodId,

        @NotNull
        @DecimalMin("0.1")
        @DecimalMax("5000.0")
        @Digits(integer = 4, fraction = 2)
        @Schema(description = "实际食用重量（克）", example = "350")
        BigDecimal grams
) {
}
