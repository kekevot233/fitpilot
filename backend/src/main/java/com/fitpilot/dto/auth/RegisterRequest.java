package com.fitpilot.dto.auth;

import com.fitpilot.domain.ActivityLevel;
import com.fitpilot.domain.Gender;
import com.fitpilot.domain.Goal;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 254)
        @Schema(example = "zhengyi@example.com")
        String email,

        @NotBlank @Size(min = 8, max = 72)
        @Schema(example = "FitPilot123")
        String password,

        @NotBlank @Size(max = 40)
        @Schema(example = "Zhengyi")
        String nickname,

        @NotNull Gender gender,
        @NotNull @Min(14) @Max(100) Integer age,
        @NotNull @DecimalMin("100.0") @DecimalMax("250.0") Double heightCm,
        @NotNull @DecimalMin("30.0") @DecimalMax("350.0") Double weightKg,
        @NotNull ActivityLevel activityLevel,
        @NotNull Goal goal,
        @Min(1) @Max(7) Integer weeklyTrainingFrequency
) {
}
