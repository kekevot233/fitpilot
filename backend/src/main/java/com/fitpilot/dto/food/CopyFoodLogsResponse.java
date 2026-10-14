package com.fitpilot.dto.food;

import java.time.LocalDate;

public record CopyFoodLogsResponse(
        LocalDate sourceDate,
        LocalDate targetDate,
        int copiedCount
) {
}
