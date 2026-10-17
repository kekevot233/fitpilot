package com.fitpilot.service;

import com.fitpilot.domain.ActivityLevel;
import com.fitpilot.domain.Food;
import com.fitpilot.domain.FoodLog;
import com.fitpilot.domain.Gender;
import com.fitpilot.domain.Goal;
import com.fitpilot.domain.MealType;
import com.fitpilot.domain.NutritionTarget;
import com.fitpilot.domain.User;
import com.fitpilot.dto.food.DailyDashboardResponse;
import com.fitpilot.exception.ResourceConflictException;
import com.fitpilot.exception.ResourceNotFoundException;
import com.fitpilot.repository.FoodLogRepository;
import com.fitpilot.repository.FoodRepository;
import com.fitpilot.repository.NutritionTargetRepository;
import com.fitpilot.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FoodLogServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private FoodRepository foodRepository;

    @Mock
    private FoodLogRepository foodLogRepository;

    @Mock
    private NutritionTargetRepository nutritionTargetRepository;

    @Spy
    private FoodNutritionCalculator foodNutritionCalculator = new FoodNutritionCalculator();

    @InjectMocks
    private FoodLogService service;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void summarizesDailyIntakeWithOrWithoutATarget(boolean hasTarget) {
        long userId = 1L;
        LocalDate date = LocalDate.of(2026, 9, 13);
        User user = new User(
                "Zhengyi", Gender.MALE, 22, 183.0, 76.0,
                ActivityLevel.MODERATE, Goal.LEAN_BULK, 4
        );
        Food rice = new Food(
                "熟米饭", new BigDecimal("116"), new BigDecimal("2.6"),
                new BigDecimal("25.9"), new BigDecimal("0.3")
        );
        FoodLog log = new FoodLog(
                user, rice, new BigDecimal("350"), MealType.LUNCH,
                LocalDateTime.of(2026, 9, 13, 12, 0)
        );
        NutritionTarget target = new NutritionTarget(
                user, 1799, 2788, 3038, 152, 454, 68, date
        );
        ReflectionTestUtils.setField(rice, "id", 1L);
        ReflectionTestUtils.setField(log, "id", 1L);

        when(userRepository.existsById(userId)).thenReturn(true);
        when(nutritionTargetRepository
                .findTopByUserIdAndEffectiveDateLessThanEqualOrderByEffectiveDateDescIdDesc(userId, date))
                .thenReturn(hasTarget ? Optional.of(target) : Optional.empty());
        when(foodLogRepository
                .findByUserIdAndEatenAtGreaterThanEqualAndEatenAtLessThanOrderByEatenAtAsc(
                        userId, date.atStartOfDay(), date.plusDays(1).atStartOfDay()
                ))
                .thenReturn(List.of(log));

        DailyDashboardResponse response = service.getDailyDashboard(userId, date);

        assertThat(response.consumed().calories()).isEqualByComparingTo("406.0");
        assertThat(response.consumed().protein()).isEqualByComparingTo("9.1");
        assertThat(response.consumed().carbs()).isEqualByComparingTo("90.7");
        assertThat(response.consumed().fat()).isEqualByComparingTo("1.1");
        assertThat(response.foodLogs()).hasSize(1);
        if (hasTarget) {
            assertThat(response.remaining().calories()).isEqualByComparingTo("2632.0");
        } else {
            assertThat(response.target()).isNull();
            assertThat(response.remaining()).isNull();
        }
        verify(nutritionTargetRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void returnsAnEmptyHistoricalDashboardWithoutCreatingATarget() {
        LocalDate date = LocalDate.of(2026, 10, 14);
        when(userRepository.existsById(1L)).thenReturn(true);

        var response = service.getDailyDashboard(1L, date);

        assertThat(response.target()).isNull();
        assertThat(response.remaining()).isNull();
        assertThat(response.consumed().calories()).isZero();
        assertThat(response.foodLogs()).isEmpty();
        verify(nutritionTargetRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void stillRejectsADashboardForAMissingUser() {
        assertThatThrownBy(() -> service.getDailyDashboard(99L, LocalDate.of(2026, 10, 14)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void copiesAllFoodLogsToAnotherDateWhileKeepingTheirTimeAndMeal() {
        long userId = 1L;
        LocalDate sourceDate = LocalDate.of(2026, 10, 10);
        LocalDate targetDate = LocalDate.of(2026, 10, 11);
        User user = new User("Zhengyi", Gender.MALE, 22, 183.0, 76.0,
                ActivityLevel.MODERATE, Goal.LEAN_BULK, 4);
        Food rice = new Food("熟米饭", new BigDecimal("116"), new BigDecimal("2.6"),
                new BigDecimal("25.9"), new BigDecimal("0.3"));
        Food egg = new Food("鸡蛋", new BigDecimal("144"), new BigDecimal("13.3"),
                new BigDecimal("2.8"), new BigDecimal("8.8"));
        List<FoodLog> sourceLogs = List.of(
                new FoodLog(user, egg, new BigDecimal("100"), MealType.BREAKFAST,
                        LocalDateTime.of(2026, 10, 10, 8, 15)),
                new FoodLog(user, rice, new BigDecimal("350"), MealType.LUNCH,
                        LocalDateTime.of(2026, 10, 10, 12, 30))
        );
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(foodLogRepository.existsByUserIdAndEatenAtGreaterThanEqualAndEatenAtLessThan(
                userId, targetDate.atStartOfDay(), targetDate.plusDays(1).atStartOfDay())).thenReturn(false);
        when(foodLogRepository.findByUserIdAndEatenAtGreaterThanEqualAndEatenAtLessThanOrderByEatenAtAsc(
                userId, sourceDate.atStartOfDay(), sourceDate.plusDays(1).atStartOfDay())).thenReturn(sourceLogs);

        var response = service.copyDay(userId, sourceDate, targetDate);

        assertThat(response.copiedCount()).isEqualTo(2);
        ArgumentCaptor<List> copiesCaptor = ArgumentCaptor.forClass(List.class);
        verify(foodLogRepository).saveAll(copiesCaptor.capture());
        List<FoodLog> copies = (List<FoodLog>) copiesCaptor.getValue();
        assertThat(copies).extracting(FoodLog::getEatenAt)
                .containsExactly(
                        LocalDateTime.of(2026, 10, 11, 8, 15),
                        LocalDateTime.of(2026, 10, 11, 12, 30)
                );
        assertThat(copies).extracting(FoodLog::getMealType)
                .containsExactly(MealType.BREAKFAST, MealType.LUNCH);
        assertThat(copies).extracting(FoodLog::getGrams)
                .containsExactly(new BigDecimal("100"), new BigDecimal("350"));
    }

    @Test
    void refusesToCopyIntoADateThatAlreadyHasLogs() {
        long userId = 1L;
        LocalDate sourceDate = LocalDate.of(2026, 10, 10);
        LocalDate targetDate = LocalDate.of(2026, 10, 11);
        User user = new User("Zhengyi", Gender.MALE, 22, 183.0, 76.0,
                ActivityLevel.MODERATE, Goal.LEAN_BULK, 4);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(foodLogRepository.existsByUserIdAndEatenAtGreaterThanEqualAndEatenAtLessThan(
                userId, targetDate.atStartOfDay(), targetDate.plusDays(1).atStartOfDay())).thenReturn(true);

        assertThatThrownBy(() -> service.copyDay(userId, sourceDate, targetDate))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("target date");
        verify(foodLogRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void reportsWhenTheSourceDateHasNoLogs() {
        long userId = 1L;
        LocalDate sourceDate = LocalDate.of(2026, 10, 10);
        LocalDate targetDate = LocalDate.of(2026, 10, 11);
        User user = new User("Zhengyi", Gender.MALE, 22, 183.0, 76.0,
                ActivityLevel.MODERATE, Goal.LEAN_BULK, 4);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(foodLogRepository.findByUserIdAndEatenAtGreaterThanEqualAndEatenAtLessThanOrderByEatenAtAsc(
                userId, sourceDate.atStartOfDay(), sourceDate.plusDays(1).atStartOfDay())).thenReturn(List.of());

        assertThatThrownBy(() -> service.copyDay(userId, sourceDate, targetDate))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("source date");
        verify(foodLogRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
    }
}
