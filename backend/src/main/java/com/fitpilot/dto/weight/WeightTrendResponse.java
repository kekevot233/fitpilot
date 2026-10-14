package com.fitpilot.dto.weight;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record WeightTrendResponse(
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal firstWeightKg,
        BigDecimal latestWeightKg,
        BigDecimal changeKg,
        List<WeightTrendPointResponse> points
) {
}
