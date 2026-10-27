package com.fitpilot.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Read-only projection; no food or user entities are loaded for a trend. */
public interface DailyNutritionSummary {
    LocalDate getDate();
    BigDecimal getCalories();
    BigDecimal getProtein();
    BigDecimal getCarbs();
    BigDecimal getFat();
    int getLogCount();
}
