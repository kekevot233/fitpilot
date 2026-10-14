package com.fitpilot.dto.food;

import java.math.BigDecimal;

public record AiNutritionEstimateResponse(
        String dishName,
        BigDecimal grams,
        NutritionValuesResponse nutritionPer100g,
        NutritionValuesResponse totalNutrition,
        String assumptions,
        Confidence confidence
) {
    public enum Confidence {
        LOW,
        MEDIUM,
        HIGH
    }
}
