package com.fitpilot.service;

import com.fitpilot.repository.DailyNutritionSummary;
import com.fitpilot.domain.NutritionTarget;
import com.fitpilot.dto.food.NutritionTrendPointResponse;
import com.fitpilot.dto.food.NutritionTrendResponse;
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

    public NutritionTrendService(UserRepository userRepository,
                                 FoodLogRepository foodLogRepository,
                                 NutritionTargetRepository nutritionTargetRepository) {
        this.userRepository = userRepository;
        this.foodLogRepository = foodLogRepository;
        this.nutritionTargetRepository = nutritionTargetRepository;
    }

    @Transactional(readOnly = true)
    public NutritionTrendResponse getTrend(long userId, LocalDate endDate, int days) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User with id " + userId + " was not found.");
        }

        LocalDate startDate = endDate.minusDays(days - 1L);
        List<DailyNutritionSummary> summaries = foodLogRepository
                .summarizeNutritionByDate(
                        userId, startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay()
                );
        Map<LocalDate, DailyNutritionSummary> totalsByDate = new HashMap<>();
        summaries.forEach(summary -> totalsByDate.put(summary.getDate(), summary));
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

            DailyNutritionSummary totals = totalsByDate.get(date);
            BigDecimal calories = totals == null ? BigDecimal.ZERO : totals.getCalories();
            BigDecimal protein = totals == null ? BigDecimal.ZERO : totals.getProtein();
            BigDecimal carbs = totals == null ? BigDecimal.ZERO : totals.getCarbs();
            BigDecimal fat = totals == null ? BigDecimal.ZERO : totals.getFat();
            int logCount = totals == null ? 0 : totals.getLogCount();
            int targetCalories = currentTarget == null ? 0 : currentTarget.getCalories();
            points.add(new NutritionTrendPointResponse(
                    date, targetCalories,
                    display(calories), display(protein), display(carbs), display(fat), logCount
            ));

            if (logCount > 0) {
                daysLogged++;
                loggedCalories = loggedCalories.add(calories);
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

    private BigDecimal average(BigDecimal total, int count) {
        if (count == 0) return BigDecimal.ZERO.setScale(DISPLAY_SCALE);
        return total.divide(BigDecimal.valueOf(count), DISPLAY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal display(BigDecimal value) {
        return value.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP);
    }

}
