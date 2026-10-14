package com.fitpilot.dto.user;

import com.fitpilot.domain.Goal;
import jakarta.validation.constraints.NotNull;

public record UpdateNutritionPlanRequest(@NotNull Goal goal) {
}
