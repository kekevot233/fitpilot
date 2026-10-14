package com.fitpilot.dto.food;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.time.LocalDate;

public record CopyFoodLogsRequest(
        @NotNull @PastOrPresent LocalDate sourceDate,
        @NotNull @PastOrPresent LocalDate targetDate
) {
}
