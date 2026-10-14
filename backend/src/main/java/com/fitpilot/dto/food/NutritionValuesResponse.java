package com.fitpilot.dto.food;

import java.math.BigDecimal;

public record NutritionValuesResponse(
        BigDecimal calories,
        BigDecimal protein,
        BigDecimal carbs,
        BigDecimal fat
) {
}
