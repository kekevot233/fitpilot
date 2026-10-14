package com.fitpilot.dto.training;

import java.time.LocalDate;
import java.util.List;

public record WorkoutResponse(
        long id,
        Long trainingPlanId,
        LocalDate workoutDate,
        String workoutType,
        List<WorkoutSetResponse> sets
) {
}
