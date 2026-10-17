package com.fitpilot.service;

import com.fitpilot.domain.ActivityLevel;
import com.fitpilot.domain.Food;
import com.fitpilot.domain.FoodLog;
import com.fitpilot.domain.Gender;
import com.fitpilot.domain.Goal;
import com.fitpilot.domain.MealType;
import com.fitpilot.domain.NutritionTarget;
import com.fitpilot.domain.User;
import com.fitpilot.dto.food.NutritionTrendResponse;
import com.fitpilot.repository.FoodLogRepository;
import com.fitpilot.repository.NutritionTargetRepository;
import com.fitpilot.repository.UserRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NutritionTrendServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private FoodLogRepository foodLogRepository;
    @Mock
    private NutritionTargetRepository nutritionTargetRepository;
    @Spy
    private FoodNutritionCalculator foodNutritionCalculator = new FoodNutritionCalculator();
    @InjectMocks
    private NutritionTrendService service;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void aggregatesLoggedDaysAndUsesTheTargetEffectiveOnEachDate(boolean hasEarlierTarget) {
        long userId = 7L;
        LocalDate endDate = LocalDate.of(2026, 10, 11);
        User user = new User(
                "Zhengyi", Gender.MALE, 22, 183.0, 76.0,
                ActivityLevel.MODERATE, Goal.FAT_LOSS, 4
        );
        Food rice = new Food(
                "熟米饭", new BigDecimal("130"), new BigDecimal("2.69"),
                new BigDecimal("28.17"), new BigDecimal("0.28")
        );
        FoodLog first = new FoodLog(
                user, rice, new BigDecimal("100"), MealType.LUNCH,
                LocalDateTime.of(2026, 10, 9, 12, 0)
        );
        FoodLog second = new FoodLog(
                user, rice, new BigDecimal("200"), MealType.DINNER,
                LocalDateTime.of(2026, 10, 11, 18, 0)
        );
        NutritionTarget bulk = new NutritionTarget(user, 1800, 2750, 3000, 150, 450, 70,
                LocalDate.of(2026, 10, 1));
        NutritionTarget fatLoss = new NutritionTarget(user, 1800, 2750, 2400, 150, 300, 70,
                LocalDate.of(2026, 10, 10));

        when(userRepository.existsById(userId)).thenReturn(true);
        when(foodLogRepository.findByUserIdAndEatenAtGreaterThanEqualAndEatenAtLessThanOrderByEatenAtAsc(
                userId, LocalDate.of(2026, 10, 5).atStartOfDay(), LocalDate.of(2026, 10, 12).atStartOfDay()
        )).thenReturn(List.of(first, second));
        when(nutritionTargetRepository
                .findByUserIdAndEffectiveDateLessThanEqualOrderByEffectiveDateAscIdAsc(userId, endDate))
                .thenReturn(hasEarlierTarget ? List.of(bulk, fatLoss) : List.of(fatLoss));

        NutritionTrendResponse response = service.getTrend(userId, endDate, 7);

        assertThat(response.points()).hasSize(7);
        assertThat(response.daysLogged()).isEqualTo(2);
        assertThat(response.averageCalories()).isEqualByComparingTo("195.0");
        assertThat(response.averageTargetCalories()).isEqualByComparingTo(hasEarlierTarget ? "2700.0" : "2400.0");
        assertThat(response.points().get(4).date()).isEqualTo(LocalDate.of(2026, 10, 9));
        assertThat(response.points().get(4).targetCalories()).isEqualTo(hasEarlierTarget ? 3000 : 0);
        assertThat(response.points().get(5).targetCalories()).isEqualTo(2400);
        assertThat(response.points().get(6).calories()).isEqualByComparingTo("260.0");
    }
}
