package com.fitpilot.dto.weight;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WeightLogResponse(
        long id,
        LocalDate recordedDate,
        BigDecimal weightKg
) {
}
