package com.fitpilot.service;

import com.fitpilot.domain.ActivityLevel;
import com.fitpilot.domain.Gender;
import com.fitpilot.domain.Goal;
import com.fitpilot.dto.nutrition.NutritionCalculationRequest;
import com.fitpilot.dto.nutrition.NutritionCalculationResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NutritionCalculationServiceTest {

    private final NutritionCalculationService service = new NutritionCalculationService();

    @Test
    void calculatesNutritionTargetsForLeanBulk() {
        NutritionCalculationRequest request = new NutritionCalculationRequest(
                Gender.MALE, 22, 183.0, 76.0, ActivityLevel.MODERATE, Goal.LEAN_BULK
        );

        NutritionCalculationResponse response = service.calculate(request);

        assertThat(response.bmr()).isEqualTo(1799);
        assertThat(response.tdee()).isEqualTo(2788);
        assertThat(response.targetCalories()).isEqualTo(3038);
        assertThat(response.protein()).isEqualTo(152);
        assertThat(response.fat()).isEqualTo(68);
        assertThat(response.carbs()).isEqualTo(454);
    }
}
