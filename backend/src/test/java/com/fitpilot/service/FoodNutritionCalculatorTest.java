package com.fitpilot.service;

import com.fitpilot.domain.Food;
import com.fitpilot.dto.food.NutritionValuesResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class FoodNutritionCalculatorTest {

    private final FoodNutritionCalculator calculator = new FoodNutritionCalculator();

    @Test
    void calculatesNutritionFromPer100GramValues() {
        Food rice = new Food(
                "熟米饭",
                new BigDecimal("130"),
                new BigDecimal("2.69"),
                new BigDecimal("28.17"),
                new BigDecimal("0.28")
        );

        NutritionValuesResponse result = calculator.calculate(rice, new BigDecimal("350"));

        assertThat(result.calories()).isEqualByComparingTo("455.0");
        assertThat(result.protein()).isEqualByComparingTo("9.4");
        assertThat(result.carbs()).isEqualByComparingTo("98.6");
        assertThat(result.fat()).isEqualByComparingTo("1.0");
    }
}
