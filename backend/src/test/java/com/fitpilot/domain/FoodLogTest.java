package com.fitpilot.domain;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class FoodLogTest {

    private final User user = new User(
            "Zhengyi", Gender.MALE, 22, 183.0, 76.0,
            ActivityLevel.MODERATE, Goal.LEAN_BULK, 4
    );

    @Test
    void capturesFoodNameAndNutritionWhenLogIsCreated() {
        Food food = food(1L, "自制鸡胸饭", "155", "18", "12", "4");
        FoodLog log = log(food);

        food.updateDetails(
                "新版鸡胸饭", new BigDecimal("180"), new BigDecimal("20"),
                new BigDecimal("15"), new BigDecimal("5")
        );

        assertThat(log.getFoodNameSnapshot()).isEqualTo("自制鸡胸饭");
        assertThat(log.getCaloriesPer100gSnapshot()).isEqualByComparingTo("155");
        assertThat(log.getProteinPer100gSnapshot()).isEqualByComparingTo("18");
        assertThat(log.getCarbsPer100gSnapshot()).isEqualByComparingTo("12");
        assertThat(log.getFatPer100gSnapshot()).isEqualByComparingTo("4");
    }

    @Test
    void editingGramsForTheSameFoodKeepsTheOriginalSnapshot() {
        Food food = food(1L, "自制鸡胸饭", "155", "18", "12", "4");
        FoodLog log = log(food);
        food.updateDetails(
                "新版鸡胸饭", new BigDecimal("180"), new BigDecimal("20"),
                new BigDecimal("15"), new BigDecimal("5")
        );

        log.update(food, new BigDecimal("300"), MealType.DINNER,
                LocalDateTime.of(2026, 10, 12, 18, 30));

        assertThat(log.getFoodNameSnapshot()).isEqualTo("自制鸡胸饭");
        assertThat(log.getCaloriesPer100gSnapshot()).isEqualByComparingTo("155");
        assertThat(log.getGrams()).isEqualByComparingTo("300");
        assertThat(log.getMealType()).isEqualTo(MealType.DINNER);
    }

    @Test
    void selectingADifferentFoodReplacesTheSnapshot() {
        Food first = food(1L, "自制鸡胸饭", "155", "18", "12", "4");
        Food replacement = food(2L, "牛肉饭", "190", "16", "20", "5");
        FoodLog log = log(first);

        log.update(replacement, new BigDecimal("250"), MealType.LUNCH,
                LocalDateTime.of(2026, 10, 12, 12, 30));

        assertThat(log.getFood()).isSameAs(replacement);
        assertThat(log.getFoodNameSnapshot()).isEqualTo("牛肉饭");
        assertThat(log.getCaloriesPer100gSnapshot()).isEqualByComparingTo("190");
    }

    @Test
    void copyingALogKeepsItsOriginalSnapshot() {
        Food food = food(1L, "自制鸡胸饭", "155", "18", "12", "4");
        FoodLog original = log(food);
        food.updateDetails(
                "新版鸡胸饭", new BigDecimal("180"), new BigDecimal("20"),
                new BigDecimal("15"), new BigDecimal("5")
        );

        FoodLog copy = original.copyTo(user, LocalDateTime.of(2026, 10, 13, 12, 0));

        assertThat(copy.getFoodNameSnapshot()).isEqualTo("自制鸡胸饭");
        assertThat(copy.getCaloriesPer100gSnapshot()).isEqualByComparingTo("155");
        assertThat(copy.getEatenAt()).isEqualTo(LocalDateTime.of(2026, 10, 13, 12, 0));
    }

    private FoodLog log(Food food) {
        return new FoodLog(
                user, food, new BigDecimal("200"), MealType.LUNCH,
                LocalDateTime.of(2026, 10, 12, 12, 0)
        );
    }

    private Food food(long id, String name, String calories, String protein, String carbs, String fat) {
        Food food = new Food(
                name, new BigDecimal(calories), new BigDecimal(protein),
                new BigDecimal(carbs), new BigDecimal(fat)
        );
        ReflectionTestUtils.setField(food, "id", id);
        return food;
    }
}
