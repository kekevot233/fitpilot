package com.fitpilot.service;

import com.fitpilot.domain.*;
import com.fitpilot.exception.ResourceNotFoundException;
import com.fitpilot.dto.user.UpdateUserProfileRequest;
import com.fitpilot.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserProfileServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final NutritionTargetRepository targets = mock(NutritionTargetRepository.class);
    private final UserProfileService service = new UserProfileService(users, targets, new NutritionCalculationService());
    private final User user = new User("Test", Gender.MALE, 22, 183.0, 76.0,
            ActivityLevel.MODERATE, Goal.LEAN_BULK, 4);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(user, "id", 1L);
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(targets.save(any(NutritionTarget.class))).thenAnswer(call -> call.getArgument(0));
    }

    @ParameterizedTest
    @CsvSource({"FAT_LOSS,2388,292", "MAINTAIN,2788,392", "LEAN_BULK,3038,454"})
    void recalculatesAllTargetsUsingSavedProfile(Goal goal, int calories, int carbs) {
        var response = service.updateNutritionPlan(1L, goal);

        assertThat(user.getGoal()).isEqualTo(goal);
        assertThat(response.goal()).isEqualTo(goal);
        assertThat(response.nutritionTarget().targetCalories()).isEqualTo(calories);
        assertThat(response.nutritionTarget().protein()).isEqualTo(152);
        assertThat(response.nutritionTarget().fat()).isEqualTo(68);
        assertThat(response.nutritionTarget().carbs()).isEqualTo(carbs);
        assertThat(response.nutritionTarget().effectiveDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void retainsHistoricalTargetAndUsesLastChangeForToday() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        NutritionTarget previous = new NutritionTarget(user, 1799, 2788, 3038, 152, 454, 68, yesterday);
        List<NutritionTarget> history = new ArrayList<>(List.of(previous));
        when(targets.save(any(NutritionTarget.class))).thenAnswer(call -> {
            NutritionTarget target = call.getArgument(0);
            history.add(target);
            return target;
        });
        when(targets.findTopByUserIdAndEffectiveDateLessThanEqualOrderByEffectiveDateDescIdDesc(eq(1L), any()))
                .thenAnswer(call -> {
                    LocalDate date = call.getArgument(1);
                    return history.reversed().stream().filter(target -> !target.getEffectiveDate().isAfter(date)).findFirst();
                });
        when(users.existsById(1L)).thenReturn(true);
        FoodLogRepository logs = mock(FoodLogRepository.class);
        FoodLogService dashboard = new FoodLogService(users, mock(FoodRepository.class), logs, targets,
                new FoodNutritionCalculator());

        service.updateNutritionPlan(1L, Goal.FAT_LOSS);
        assertThat(dashboard.getDailyDashboard(1L, today).target().calories()).isEqualByComparingTo("2388");
        service.updateNutritionPlan(1L, Goal.MAINTAIN);

        assertThat(history).hasSize(3);
        assertThat(previous.getCalories()).isEqualTo(3038);
        assertThat(dashboard.getDailyDashboard(1L, yesterday).target().calories()).isEqualByComparingTo("3038");
        assertThat(dashboard.getDailyDashboard(1L, today).target().calories()).isEqualByComparingTo("2788");
        assertThat(dashboard.getDailyDashboard(1L, today).remaining().calories()).isEqualByComparingTo("2788");
    }

    @Test
    void rejectsMissingUserWithoutSavingTarget() {
        assertThatThrownBy(() -> service.updateNutritionPlan(99L, Goal.FAT_LOSS))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(targets, never()).save(any());
    }

    @Test
    void updatesBodyProfileAndRecalculatesTargetUsingCurrentGoal() {
        var request = new UpdateUserProfileRequest(23, 180.0, 80.0, ActivityLevel.LIGHT, 3);

        var response = service.updateProfile(1L, request);

        assertThat(user.getAge()).isEqualTo(23);
        assertThat(user.getHeightCm()).isEqualTo(180.0);
        assertThat(user.getWeightKg()).isEqualTo(80.0);
        assertThat(user.getActivityLevel()).isEqualTo(ActivityLevel.LIGHT);
        assertThat(user.getWeeklyTrainingFrequency()).isEqualTo(3);
        assertThat(response.goal()).isEqualTo(Goal.LEAN_BULK);
        assertThat(response.nutritionTarget().bmr()).isEqualTo(1815);
        assertThat(response.nutritionTarget().tdee()).isEqualTo(2496);
        assertThat(response.nutritionTarget().targetCalories()).isEqualTo(2746);
        assertThat(response.nutritionTarget().protein()).isEqualTo(160);
        assertThat(response.nutritionTarget().fat()).isEqualTo(72);
        assertThat(response.nutritionTarget().carbs()).isEqualTo(364);
        assertThat(response.nutritionTarget().effectiveDate()).isEqualTo(LocalDate.now());
        verify(targets).save(any(NutritionTarget.class));
    }

    @Test
    void rejectsProfileUpdateForMissingUserWithoutSavingTarget() {
        var request = new UpdateUserProfileRequest(23, 180.0, 80.0, ActivityLevel.LIGHT, 3);

        assertThatThrownBy(() -> service.updateProfile(99L, request))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(targets, never()).save(any());
    }
}
