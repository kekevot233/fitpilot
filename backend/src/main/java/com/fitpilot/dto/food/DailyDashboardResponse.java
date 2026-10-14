package com.fitpilot.dto.food;

import java.time.LocalDate;
import java.util.List;

public record DailyDashboardResponse(
        long userId,
        LocalDate date,
        NutritionValuesResponse target,
        NutritionValuesResponse consumed,
        NutritionValuesResponse remaining,
        List<FoodLogResponse> foodLogs
) {
}
