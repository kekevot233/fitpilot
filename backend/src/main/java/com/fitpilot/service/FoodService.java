package com.fitpilot.service;

import com.fitpilot.domain.Food;
import com.fitpilot.domain.User;
import com.fitpilot.dto.food.CalculateFoodNutritionRequest;
import com.fitpilot.dto.food.CreateFoodRequest;
import com.fitpilot.dto.food.FoodNutritionCalculationResponse;
import com.fitpilot.dto.food.FoodResponse;
import com.fitpilot.exception.ResourceConflictException;
import com.fitpilot.exception.ResourceNotFoundException;
import com.fitpilot.repository.FoodLogRepository;
import com.fitpilot.repository.FoodRepository;
import com.fitpilot.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Profile;

import java.util.List;

@Service
@Profile("!no-db")
public class FoodService {

    private final FoodRepository foodRepository;
    private final FoodLogRepository foodLogRepository;
    private final UserRepository userRepository;
    private final FoodNutritionCalculator foodNutritionCalculator;

    public FoodService(FoodRepository foodRepository,
                       FoodLogRepository foodLogRepository,
                       UserRepository userRepository,
                       FoodNutritionCalculator foodNutritionCalculator) {
        this.foodRepository = foodRepository;
        this.foodLogRepository = foodLogRepository;
        this.userRepository = userRepository;
        this.foodNutritionCalculator = foodNutritionCalculator;
    }

    @Transactional
    public FoodResponse create(long userId, CreateFoodRequest request) {
        String name = request.name().trim();
        if (foodRepository.existsByOwnerIsNullAndNameIgnoreCase(name)) {
            throw new ResourceConflictException("A built-in food named '" + name + "' already exists.");
        }
        if (foodRepository.existsActiveCustomName(userId, name, -1L)) {
            throw new ResourceConflictException("You already have a custom food named '" + name + "'.");
        }

        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User with id " + userId + " was not found."));

        Food food = foodRepository.save(new Food(
                owner, name,
                request.caloriesPer100g(),
                request.proteinPer100g(),
                request.carbsPer100g(),
                request.fatPer100g()
        ));
        return toResponse(food);
    }

    @Transactional(readOnly = true)
    public List<FoodResponse> findAll(long userId, String keyword) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        List<Food> foods = foodRepository.findVisibleToUser(userId, normalizedKeyword);

        return foods.stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FoodResponse> findRecentlyLoggedFoods(long userId, int limit) {
        return foodLogRepository.findRecentlyLoggedFoods(userId, PageRequest.of(0, limit)).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FoodResponse> findCustomFoods(long userId) {
        return foodRepository.findActiveCustomFoods(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public FoodResponse updateCustomFood(long userId, long foodId, CreateFoodRequest request) {
        Food food = findOwnedCustomFood(userId, foodId);

        String name = request.name().trim();
        if (foodRepository.existsByOwnerIsNullAndNameIgnoreCase(name)) {
            throw new ResourceConflictException("A built-in food named '" + name + "' already exists.");
        }
        if (foodRepository.existsActiveCustomName(userId, name, foodId)) {
            throw new ResourceConflictException("You already have a custom food named '" + name + "'.");
        }

        food.updateDetails(
                name,
                request.caloriesPer100g(), request.proteinPer100g(),
                request.carbsPer100g(), request.fatPer100g()
        );
        return toResponse(food);
    }

    @Transactional
    public void archiveCustomFood(long userId, long foodId) {
        Food food = findOwnedCustomFood(userId, foodId);
        food.archive();
    }

    @Transactional(readOnly = true)
    public FoodNutritionCalculationResponse calculate(long userId, CalculateFoodNutritionRequest request) {
        Food food = foodRepository.findAccessibleById(request.foodId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Food with id " + request.foodId() + " was not found."
                ));

        return new FoodNutritionCalculationResponse(
                food.getId(),
                food.getName(),
                request.grams(),
                foodNutritionCalculator.calculate(food, request.grams())
        );
    }

    private Food findOwnedCustomFood(long userId, long foodId) {
        return foodRepository.findActiveCustomById(foodId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Custom food with id " + foodId + " was not found."
                ));
    }

    private FoodResponse toResponse(Food food) {
        return new FoodResponse(
                food.getId(),
                food.getName(),
                food.getCaloriesPer100g(),
                food.getProteinPer100g(),
                food.getCarbsPer100g(),
                food.getFatPer100g()
        );
    }
}
