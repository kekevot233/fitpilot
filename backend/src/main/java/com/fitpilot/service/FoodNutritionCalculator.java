package com.fitpilot.service;

import com.fitpilot.domain.Food;
import com.fitpilot.dto.food.NutritionValuesResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class FoodNutritionCalculator {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final int DISPLAY_SCALE = 1;

    public NutritionValuesResponse calculate(Food food, BigDecimal grams) {
        return calculate(
                food.getCaloriesPer100g(),
                food.getProteinPer100g(),
                food.getCarbsPer100g(),
                food.getFatPer100g(),
                grams
        );
    }

    public NutritionValuesResponse calculate(BigDecimal caloriesPer100g,
                                               BigDecimal proteinPer100g,
                                               BigDecimal carbsPer100g,
                                               BigDecimal fatPer100g,
                                               BigDecimal grams) {
        BigDecimal ratio = grams.divide(ONE_HUNDRED, 6, RoundingMode.HALF_UP);

        return values(
                caloriesPer100g.multiply(ratio),
                proteinPer100g.multiply(ratio),
                carbsPer100g.multiply(ratio),
                fatPer100g.multiply(ratio)
        );
    }

    private NutritionValuesResponse values(BigDecimal calories, BigDecimal protein,
                                             BigDecimal carbs, BigDecimal fat) {
        return new NutritionValuesResponse(
                calories.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP),
                protein.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP),
                carbs.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP),
                fat.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP)
        );
    }
}
