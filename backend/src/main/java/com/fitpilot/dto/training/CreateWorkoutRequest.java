package com.fitpilot.dto.training;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record CreateWorkoutRequest(
        Long trainingPlanId,
        @NotNull LocalDate workoutDate,
        @NotBlank @Size(max = 50) String workoutType,
        @NotEmpty List<@Valid WorkoutSetRequest> sets
) {
}
