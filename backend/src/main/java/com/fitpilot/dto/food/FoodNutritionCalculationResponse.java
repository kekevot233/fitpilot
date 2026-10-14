package com.fitpilot.dto.food;

import java.math.BigDecimal;

public record FoodNutritionCalculationResponse(
        long foodId,
        String foodName,
        BigDecimal grams,
        NutritionValuesResponse nutrition
) {
}
