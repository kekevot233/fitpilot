package com.fitpilot.service;

import com.fitpilot.domain.FoodLog;
import com.fitpilot.domain.NutritionTarget;
import com.fitpilot.dto.food.NutritionTrendPointResponse;
import com.fitpilot.dto.food.NutritionTrendResponse;
import com.fitpilot.dto.food.NutritionValuesResponse;
import com.fitpilot.exception.ResourceNotFoundException;
import com.fitpilot.repository.FoodLogRepository;
import com.fitpilot.repository.NutritionTargetRepository;
import com.fitpilot.repository.UserRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Profile("!no-db")
public class NutritionTrendService {

    private static final int DISPLAY_SCALE = 1;

    private final UserRepository userRepository;
    private final FoodLogRepository foodLogRepository;
    private final NutritionTargetRepository nutritionTargetRepository;
    private final FoodNutritionCalculator foodNutritionCalculator;

    public NutritionTrendService(UserRepository userRepository,
                                 FoodLogRepository foodLogRepository,
                                 NutritionTargetRepository nutritionTargetRepository,
                                 FoodNutritionCalculator foodNutritionCalculator) {
        this.userRepository = userRepository;
        this.foodLogRepository = foodLogRepository;
        this.nutritionTargetRepository = nutritionTargetRepository;
        this.foodNutritionCalculator = foodNutritionCalculator;
    }

    @Transactional(readOnly = true)
    public NutritionTrendResponse getTrend(long userId, LocalDate endDate, int days) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User with id " + userId + " was not found.");
        }

        LocalDate startDate = endDate.minusDays(days - 1L);
        List<FoodLog> logs = foodLogRepository
                .findByUserIdAndEatenAtGreaterThanEqualAndEatenAtLessThanOrderByEatenAtAsc(
                        userId, startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay()
                );
        Map<LocalDate, DailyTotals> totalsByDate = summarize(logs);
        List<NutritionTarget> targets = nutritionTargetRepository
                .findByUserIdAndEffectiveDateLessThanEqualOrderByEffectiveDateAscIdAsc(userId, endDate);

        List<NutritionTrendPointResponse> points = new ArrayList<>(days);
        NutritionTarget currentTarget = null;
        int targetIndex = 0;
        BigDecimal loggedCalories = BigDecimal.ZERO;
        BigDecimal loggedTargets = BigDecimal.ZERO;
        int daysLogged = 0;
        int loggedDaysWithTarget = 0;

        for (int offset = 0; offset < days; offset++) {
            LocalDate date = startDate.plusDays(offset);
            while (targetIndex < targets.size()
                    && !targets.get(targetIndex).getEffectiveDate().isAfter(date)) {
                currentTarget = targets.get(targetIndex++);
            }

            DailyTotals totals = totalsByDate.getOrDefault(date, new DailyTotals());
            int targetCalories = currentTarget == null ? 0 : currentTarget.getCalories();
            points.add(new NutritionTrendPointResponse(
                    date, targetCalories,
                    display(totals.calories), display(totals.protein),
                    display(totals.carbs), display(totals.fat), totals.logCount
            ));

            if (totals.logCount > 0) {
                daysLogged++;
                loggedCalories = loggedCalories.add(totals.calories);
                if (targetCalories > 0) {
                    loggedTargets = loggedTargets.add(BigDecimal.valueOf(targetCalories));
                    loggedDaysWithTarget++;
                }
            }
        }

        return new NutritionTrendResponse(
                startDate,
                endDate,
                daysLogged,
                average(loggedCalories, daysLogged),
                average(loggedTargets, loggedDaysWithTarget),
                points
        );
    }

    private Map<LocalDate, DailyTotals> summarize(List<FoodLog> logs) {
        Map<LocalDate, DailyTotals> totalsByDate = new HashMap<>();
        for (FoodLog log : logs) {
            NutritionValuesResponse nutrition = foodNutritionCalculator.calculate(
                    log.getCaloriesPer100gSnapshot(),
                    log.getProteinPer100gSnapshot(),
                    log.getCarbsPer100gSnapshot(),
                    log.getFatPer100gSnapshot(),
                    log.getGrams()
            );
            DailyTotals totals = totalsByDate.computeIfAbsent(log.getEatenAt().toLocalDate(), ignored -> new DailyTotals());
            totals.add(nutrition);
        }
        return totalsByDate;
    }

    private BigDecimal average(BigDecimal total, int count) {
        if (count == 0) return BigDecimal.ZERO.setScale(DISPLAY_SCALE);
        return total.divide(BigDecimal.valueOf(count), DISPLAY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal display(BigDecimal value) {
        return value.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP);
    }

    private static final class DailyTotals {
        private BigDecimal calories = BigDecimal.ZERO;
        private BigDecimal protein = BigDecimal.ZERO;
        private BigDecimal carbs = BigDecimal.ZERO;
        private BigDecimal fat = BigDecimal.ZERO;
        private int logCount;

        private void add(NutritionValuesResponse values) {
            calories = calories.add(values.calories());
            protein = protein.add(values.protein());
            carbs = carbs.add(values.carbs());
            fat = fat.add(values.fat());
            logCount++;
        }
    }
}
