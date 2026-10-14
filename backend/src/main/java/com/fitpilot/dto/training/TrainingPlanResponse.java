package com.fitpilot.dto.training;

import java.time.LocalDateTime;
import java.util.List;

public record TrainingPlanResponse(
        long id,
        String name,
        int weeklyFrequency,
        boolean active,
        LocalDateTime createdAt,
        List<TrainingDayResponse> days
) {
}
