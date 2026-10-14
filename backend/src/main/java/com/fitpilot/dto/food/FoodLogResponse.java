package com.fitpilot.dto.food;

import com.fitpilot.domain.MealType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FoodLogResponse(
        long id,
        long foodId,
        String foodName,
        BigDecimal grams,
        MealType mealType,
        LocalDateTime eatenAt,
        NutritionValuesResponse nutrition
) {
}
