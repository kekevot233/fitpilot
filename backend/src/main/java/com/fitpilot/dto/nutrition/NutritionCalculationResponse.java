package com.fitpilot.dto.nutrition;

public record NutritionCalculationResponse(
        int bmr,
        int tdee,
        int targetCalories,
        int protein,
        int fat,
        int carbs
) {
}
