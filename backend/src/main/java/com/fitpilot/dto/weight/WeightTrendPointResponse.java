package com.fitpilot.dto.weight;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WeightTrendPointResponse(
        LocalDate recordedDate,
        BigDecimal weightKg,
        BigDecimal sevenDayAverageKg,
        int averageSampleDays
) {
}
