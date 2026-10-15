package com.fitpilot.service;

import com.fitpilot.domain.Food;
import com.fitpilot.domain.FoodLog;
import com.fitpilot.domain.MealType;
import com.fitpilot.domain.User;
import com.fitpilot.domain.Gender;
import com.fitpilot.domain.ActivityLevel;
import com.fitpilot.domain.Goal;
import com.fitpilot.dto.food.CreateFoodRequest;
import com.fitpilot.dto.food.FoodResponse;
import com.fitpilot.dto.food.RecentFoodResponse;
import com.fitpilot.exception.ResourceConflictException;
import com.fitpilot.repository.FoodLogRepository;
import com.fitpilot.repository.FoodRepository;
import com.fitpilot.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FoodServiceTest {

    @Mock
    private FoodRepository foodRepository;
    @Mock
    private FoodLogRepository foodLogRepository;
    @Mock
    private UserRepository userRepository;
    @Spy
    private FoodNutritionCalculator foodNutritionCalculator = new FoodNutritionCalculator();
    @InjectMocks
    private FoodService service;

    @Test
    void returnsTheUserRecentFoodsInRepositoryOrder() {
        User user = user();
        Food chickenRice = food("沙县鸡腿饭", 176);
        Food soyMilk = food("无糖豆浆", 38);
        FoodLog chickenRiceLog = foodLog(user, chickenRice, "350", MealType.LUNCH,
                LocalDateTime.of(2026, 10, 15, 12, 30));
        FoodLog soyMilkLog = foodLog(user, soyMilk, "300", MealType.BREAKFAST,
                LocalDateTime.of(2026, 10, 14, 8, 0));
        when(foodLogRepository.findRecentlyLoggedFoods(
                eq(7L),
                argThat((Pageable pageable) -> pageable.getPageNumber() == 0 && pageable.getPageSize() == 6)
        )).thenReturn(List.of(chickenRiceLog, soyMilkLog));

        List<RecentFoodResponse> response = service.findRecentlyLoggedFoods(7L, 6);

        assertThat(response).extracting(RecentFoodResponse::name)
                .containsExactly("沙县鸡腿饭", "无糖豆浆");
        assertThat(response.getFirst().lastGrams()).isEqualByComparingTo("350");
        assertThat(response.getFirst().lastMealType()).isEqualTo(MealType.LUNCH);
        assertThat(response.getFirst().lastEatenAt())
                .isEqualTo(LocalDateTime.of(2026, 10, 15, 12, 30));
    }

    @Test
    void returnsOnlyBuiltInAndCurrentUsersCustomFoodsFromRepository() {
        Food rice = food("熟米饭", 116);
        Food ownMeal = food("我的鸡胸饭", 155);
        when(foodRepository.findVisibleToUser(7L, "鸡")).thenReturn(List.of(ownMeal, rice));

        List<FoodResponse> response = service.findAll(7L, " 鸡 ");

        assertThat(response).extracting(FoodResponse::name)
                .containsExactly("我的鸡胸饭", "熟米饭");
        verify(foodRepository).findVisibleToUser(7L, "鸡");
    }

    @Test
    void createsCustomFoodOwnedByAuthenticatedUser() {
        User owner = user();
        when(userRepository.findById(7L)).thenReturn(Optional.of(owner));
        when(foodRepository.save(any(Food.class))).thenAnswer(call -> {
            Food saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 99L);
            return saved;
        });
        CreateFoodRequest request = new CreateFoodRequest(
                "我的鸡胸饭", new BigDecimal("155"), new BigDecimal("18"),
                new BigDecimal("12"), new BigDecimal("4")
        );

        FoodResponse response = service.create(7L, request);

        assertThat(response.name()).isEqualTo("我的鸡胸饭");
        verify(foodRepository).save(argThat(food -> food.getOwner() == owner));
    }

    @Test
    void rejectsDuplicateCustomNameOnlyWithinTheSameUser() {
        when(foodRepository.existsActiveCustomName(7L, "我的鸡胸饭", -1L)).thenReturn(true);
        CreateFoodRequest request = new CreateFoodRequest(
                "我的鸡胸饭", new BigDecimal("155"), new BigDecimal("18"),
                new BigDecimal("12"), new BigDecimal("4")
        );

        assertThatThrownBy(() -> service.create(7L, request))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("already have");
    }

    @Test
    void updatesAnUnusedOwnedCustomFood() {
        User owner = user();
        Food custom = customFood(owner, "旧名称", 150);
        when(foodRepository.findActiveCustomById(99L, 7L)).thenReturn(Optional.of(custom));
        CreateFoodRequest request = new CreateFoodRequest(
                "新名称", new BigDecimal("165"), new BigDecimal("20"),
                new BigDecimal("10"), new BigDecimal("5")
        );

        FoodResponse response = service.updateCustomFood(7L, 99L, request);

        assertThat(response.name()).isEqualTo("新名称");
        assertThat(response.caloriesPer100g()).isEqualByComparingTo("165");
    }

    @Test
    void updatesAFoodAlreadyUsedByLogsBecauseLogsKeepSnapshots() {
        User owner = user();
        Food custom = customFood(owner, "已记录食物", 150);
        when(foodRepository.findActiveCustomById(99L, 7L)).thenReturn(Optional.of(custom));
        CreateFoodRequest request = new CreateFoodRequest(
                "修改名称", new BigDecimal("165"), new BigDecimal("20"),
                new BigDecimal("10"), new BigDecimal("5")
        );

        FoodResponse response = service.updateCustomFood(7L, 99L, request);

        assertThat(response.name()).isEqualTo("修改名称");
        assertThat(response.caloriesPer100g()).isEqualByComparingTo("165");
    }

    @Test
    void archivesOwnedCustomFoodWithoutDeletingIt() {
        User owner = user();
        Food custom = customFood(owner, "旧食物", 150);
        when(foodRepository.findActiveCustomById(99L, 7L)).thenReturn(Optional.of(custom));

        service.archiveCustomFood(7L, 99L);

        assertThat(custom.isActive()).isFalse();
    }

    private Food food(String name, int calories) {
        Food food = new Food(
                name, BigDecimal.valueOf(calories), BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE
        );
        ReflectionTestUtils.setField(food, "id", (long) calories);
        return food;
    }

    private User user() {
        User user = new User("Zhengyi", Gender.MALE, 22, 183.0, 76.0,
                ActivityLevel.MODERATE, Goal.LEAN_BULK, 4);
        ReflectionTestUtils.setField(user, "id", 7L);
        return user;
    }

    private FoodLog foodLog(User user, Food food, String grams, MealType mealType,
                            LocalDateTime eatenAt) {
        return new FoodLog(user, food, new BigDecimal(grams), mealType, eatenAt);
    }

    private Food customFood(User owner, String name, int calories) {
        Food food = new Food(
                owner, name, BigDecimal.valueOf(calories), BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE
        );
        ReflectionTestUtils.setField(food, "id", 99L);
        return food;
    }
}
