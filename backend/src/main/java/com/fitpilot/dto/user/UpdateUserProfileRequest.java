package com.fitpilot.dto.user;

import com.fitpilot.domain.ActivityLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateUserProfileRequest(
        @NotNull @Min(14) @Max(100)
        @Schema(example = "22")
        Integer age,

        @NotNull @DecimalMin("100.0") @DecimalMax("250.0")
        @Schema(example = "183.0")
        Double heightCm,

        @NotNull @DecimalMin("30.0") @DecimalMax("350.0")
        @Schema(example = "76.0")
        Double weightKg,

        @NotNull
        ActivityLevel activityLevel,

        @Min(1) @Max(7)
        @Schema(example = "4")
        Integer weeklyTrainingFrequency
) {
}
