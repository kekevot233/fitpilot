package com.fitpilot.dto.food;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record NutritionTrendResponse(
        LocalDate startDate,
        LocalDate endDate,
        int daysLogged,
        BigDecimal averageCalories,
        BigDecimal averageTargetCalories,
        List<NutritionTrendPointResponse> points
) {
}
