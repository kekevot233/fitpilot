package com.fitpilot.dto.nutrition;

import com.fitpilot.domain.ActivityLevel;
import com.fitpilot.domain.Gender;
import com.fitpilot.domain.Goal;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record NutritionCalculationRequest(
        @NotNull Gender gender,
        @NotNull @Min(14) @Max(100) Integer age,
        @NotNull @DecimalMin("100.0") @DecimalMax("250.0") Double heightCm,
        @NotNull @DecimalMin("30.0") @DecimalMax("350.0") Double weightKg,
        @NotNull ActivityLevel activityLevel,
        @NotNull Goal goal
) {
}
