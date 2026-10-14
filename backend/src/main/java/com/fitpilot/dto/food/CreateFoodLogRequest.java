package com.fitpilot.dto.food;

import com.fitpilot.domain.MealType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateFoodLogRequest(
        @NotNull Long foodId,
        @NotNull @DecimalMin("0.1") @DecimalMax("5000.0") @Digits(integer = 4, fraction = 2)
        BigDecimal grams,
        @NotNull MealType mealType,
        LocalDateTime eatenAt
) {
}
