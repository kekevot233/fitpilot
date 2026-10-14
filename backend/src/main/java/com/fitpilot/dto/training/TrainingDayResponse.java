package com.fitpilot.dto.training;

import java.util.List;

public record TrainingDayResponse(
        int dayNumber,
        String dayName,
        List<TrainingPlanExerciseResponse> exercises
) {
}
