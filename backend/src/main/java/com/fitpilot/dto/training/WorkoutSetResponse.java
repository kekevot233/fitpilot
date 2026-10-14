package com.fitpilot.dto.training;

import java.math.BigDecimal;

public record WorkoutSetResponse(
        long id,
        String exerciseName,
        int setNumber,
        BigDecimal weightKg,
        int reps,
        int rir
) {
}
