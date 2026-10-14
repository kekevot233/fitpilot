package com.fitpilot.dto.user;

import java.time.LocalDate;

public record NutritionTargetResponse(
        int bmr,
        int tdee,
        int targetCalories,
        int protein,
        int carbs,
        int fat,
        LocalDate effectiveDate
) {
}
