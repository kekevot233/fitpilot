package com.fitpilot.dto.food;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

public record DailyDashboardResponse(
        long userId,
        LocalDate date,
        @Schema(nullable = true, description = "当日生效的目标；尚无目标时为 null") NutritionValuesResponse target,
        NutritionValuesResponse consumed,
        @Schema(nullable = true, description = "目标减去摄入；尚无目标时为 null") NutritionValuesResponse remaining,
        List<FoodLogResponse> foodLogs
) {
}
