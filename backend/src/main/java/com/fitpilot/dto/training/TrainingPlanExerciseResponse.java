package com.fitpilot.dto.training;

public record TrainingPlanExerciseResponse(
        long id,
        String exerciseName,
        int targetSets,
        int minReps,
        int maxReps,
        int targetRir
) {
}
