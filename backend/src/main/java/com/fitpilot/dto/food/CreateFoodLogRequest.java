package com.fitpilot.dto.food;

import com.fitpilot.domain.MealType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateFoodLogRequest(
        @NotNull @Positive(message = "食物 ID 必须为正整数。") Long foodId,
        @NotNull @DecimalMin("0.1") @DecimalMax("5000.0") @Digits(integer = 4, fraction = 2)
        BigDecimal grams,
        @NotNull MealType mealType,
        LocalDateTime eatenAt
) {
}
