package com.fitpilot.service;

import com.fitpilot.domain.Gender;
import com.fitpilot.domain.Goal;
import com.fitpilot.dto.nutrition.NutritionCalculationRequest;
import com.fitpilot.dto.nutrition.NutritionCalculationResponse;
import org.springframework.stereotype.Service;

@Service
public class NutritionCalculationService {

    private static final double PROTEIN_GRAMS_PER_KG = 2.0;
    private static final double FAT_GRAMS_PER_KG = 0.9;
    private static final int CALORIES_PER_GRAM_OF_PROTEIN = 4;
    private static final int CALORIES_PER_GRAM_OF_CARB = 4;
    private static final int CALORIES_PER_GRAM_OF_FAT = 9;

    public NutritionCalculationResponse calculate(NutritionCalculationRequest request) {
        double bmrValue = calculateBmr(request);
        int bmr = (int) Math.round(bmrValue);
        int tdee = (int) Math.round(bmrValue * request.activityLevel().getMultiplier());
        int targetCalories = tdee + request.goal().getCalorieAdjustment();

        int protein = (int) Math.round(request.weightKg() * PROTEIN_GRAMS_PER_KG);
        int fat = (int) Math.round(request.weightKg() * FAT_GRAMS_PER_KG);
        int carbCalories = targetCalories
                - protein * CALORIES_PER_GRAM_OF_PROTEIN
                - fat * CALORIES_PER_GRAM_OF_FAT;
        int carbs = Math.max(0, carbCalories / CALORIES_PER_GRAM_OF_CARB);

        return new NutritionCalculationResponse(bmr, tdee, targetCalories, protein, fat, carbs);
    }

    private double calculateBmr(NutritionCalculationRequest request) {
        double genderAdjustment = request.gender() == Gender.MALE ? 5 : -161;
        return 10 * request.weightKg()
                + 6.25 * request.heightCm()
                - 5 * request.age()
                + genderAdjustment;
    }
}
