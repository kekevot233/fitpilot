package com.fitpilot.dto.food;

import java.math.BigDecimal;
import java.time.LocalDate;

public record NutritionTrendPointResponse(
        LocalDate date,
        int targetCalories,
        BigDecimal calories,
        BigDecimal protein,
        BigDecimal carbs,
        BigDecimal fat,
        int logCount
) {
}
