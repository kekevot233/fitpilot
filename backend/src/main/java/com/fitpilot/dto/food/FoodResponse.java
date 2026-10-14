package com.fitpilot.dto.food;

import java.math.BigDecimal;

public record FoodResponse(
        long id,
        String name,
        BigDecimal caloriesPer100g,
        BigDecimal proteinPer100g,
        BigDecimal carbsPer100g,
        BigDecimal fatPer100g
) {
}
