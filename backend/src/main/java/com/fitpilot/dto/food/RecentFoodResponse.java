package com.fitpilot.dto.food;

import com.fitpilot.domain.MealType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RecentFoodResponse(
        long id,
        String name,
        BigDecimal caloriesPer100g,
        BigDecimal proteinPer100g,
        BigDecimal carbsPer100g,
        BigDecimal fatPer100g,
        BigDecimal lastGrams,
        MealType lastMealType,
        LocalDateTime lastEatenAt
) {
}
