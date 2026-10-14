package com.fitpilot.dto.user;

import com.fitpilot.domain.ActivityLevel;
import com.fitpilot.domain.Gender;
import com.fitpilot.domain.Goal;

public record UserProfileResponse(
        long id,
        String email,
        String nickname,
        Gender gender,
        int age,
        double heightCm,
        double weightKg,
        ActivityLevel activityLevel,
        Goal goal,
        int weeklyTrainingFrequency,
        NutritionTargetResponse nutritionTarget
) {
}
