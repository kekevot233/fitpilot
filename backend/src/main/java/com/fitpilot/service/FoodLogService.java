package com.fitpilot.service;

import com.fitpilot.domain.Food;
import com.fitpilot.domain.FoodLog;
import com.fitpilot.domain.NutritionTarget;
import com.fitpilot.domain.User;
import com.fitpilot.dto.food.CreateFoodLogRequest;
import com.fitpilot.dto.food.CopyFoodLogsResponse;
import com.fitpilot.dto.food.DailyDashboardResponse;
import com.fitpilot.dto.food.FoodLogResponse;
import com.fitpilot.dto.food.NutritionValuesResponse;
import com.fitpilot.exception.ResourceNotFoundException;
import com.fitpilot.exception.ResourceConflictException;
import com.fitpilot.repository.FoodLogRepository;
import com.fitpilot.repository.FoodRepository;
import com.fitpilot.repository.NutritionTargetRepository;
import com.fitpilot.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Profile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Profile("!no-db")
public class FoodLogService {

    private static final int DISPLAY_SCALE = 1;

    private final UserRepository userRepository;
    private final FoodRepository foodRepository;
    private final FoodLogRepository foodLogRepository;
    private final NutritionTargetRepository nutritionTargetRepository;
    private final FoodNutritionCalculator foodNutritionCalculator;

    public FoodLogService(UserRepository userRepository,
                          FoodRepository foodRepository,
                          FoodLogRepository foodLogRepository,
                          NutritionTargetRepository nutritionTargetRepository,
                          FoodNutritionCalculator foodNutritionCalculator) {
        this.userRepository = userRepository;
        this.foodRepository = foodRepository;
        this.foodLogRepository = foodLogRepository;
        this.nutritionTargetRepository = nutritionTargetRepository;
        this.foodNutritionCalculator = foodNutritionCalculator;
    }

    @Transactional
    public FoodLogResponse create(long userId, CreateFoodLogRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User with id " + userId + " was not found."));
        Food food = foodRepository.findAccessibleById(request.foodId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Food with id " + request.foodId() + " was not found."
                ));

        LocalDateTime eatenAt = request.eatenAt() == null ? LocalDateTime.now() : request.eatenAt();
        FoodLog log = foodLogRepository.save(new FoodLog(
                user, food, request.grams(), request.mealType(), eatenAt
        ));
        return toResponse(log);
    }

    @Transactional
    public FoodLogResponse update(long userId, long foodLogId, CreateFoodLogRequest request) {
        FoodLog log = foodLogRepository.findByIdAndUserId(foodLogId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Food log with id " + foodLogId + " was not found for user with id " + userId + "."
                ));
        Food food = foodRepository.findAccessibleById(request.foodId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Food with id " + request.foodId() + " was not found."
                ));

        LocalDateTime eatenAt = request.eatenAt() == null ? log.getEatenAt() : request.eatenAt();
        log.update(food, request.grams(), request.mealType(), eatenAt);
        return toResponse(log);
    }

    @Transactional
    public void delete(long userId, long foodLogId) {
        FoodLog log = foodLogRepository.findByIdAndUserId(foodLogId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Food log with id " + foodLogId + " was not found for user with id " + userId + "."
                ));
        foodLogRepository.delete(log);
    }

    @Transactional
    public CopyFoodLogsResponse copyDay(long userId, LocalDate sourceDate, LocalDate targetDate) {
        if (sourceDate.equals(targetDate)) {
            throw new ResourceConflictException("Source date and target date must be different.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User with id " + userId + " was not found."));

        if (foodLogRepository.existsByUserIdAndEatenAtGreaterThanEqualAndEatenAtLessThan(
                userId, targetDate.atStartOfDay(), targetDate.plusDays(1).atStartOfDay())) {
            throw new ResourceConflictException("The target date already contains food logs.");
        }

        List<FoodLog> sourceLogs = foodLogRepository
                .findByUserIdAndEatenAtGreaterThanEqualAndEatenAtLessThanOrderByEatenAtAsc(
                        userId, sourceDate.atStartOfDay(), sourceDate.plusDays(1).atStartOfDay()
                );
        if (sourceLogs.isEmpty()) {
            throw new ResourceNotFoundException("The source date does not contain food logs.");
        }

        List<FoodLog> copies = sourceLogs.stream()
                .map(log -> log.copyTo(
                        user,
                        LocalDateTime.of(targetDate, log.getEatenAt().toLocalTime())
                ))
                .toList();
        foodLogRepository.saveAll(copies);

        return new CopyFoodLogsResponse(sourceDate, targetDate, copies.size());
    }

    @Transactional(readOnly = true)
    public DailyDashboardResponse getDailyDashboard(long userId, LocalDate date) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User with id " + userId + " was not found.");
        }

        NutritionTarget target = nutritionTargetRepository
                .findTopByUserIdAndEffectiveDateLessThanEqualOrderByEffectiveDateDescIdDesc(userId, date)
                .orElse(null);

        List<FoodLogResponse> logs = foodLogRepository
                .findByUserIdAndEatenAtGreaterThanEqualAndEatenAtLessThanOrderByEatenAtAsc(
                        userId, date.atStartOfDay(), date.plusDays(1).atStartOfDay()
                )
                .stream()
                .map(this::toResponse)
                .toList();

        NutritionValuesResponse consumed = sumNutrition(logs);
        // Intake is a fact even before the user's first dated nutrition plan.
        if (target == null) {
            return new DailyDashboardResponse(userId, date, null, consumed, null, logs);
        }

        NutritionValuesResponse targetValues = values(
                BigDecimal.valueOf(target.getCalories()),
                BigDecimal.valueOf(target.getProteinG()),
                BigDecimal.valueOf(target.getCarbsG()),
                BigDecimal.valueOf(target.getFatG())
        );
        NutritionValuesResponse remaining = values(
                targetValues.calories().subtract(consumed.calories()),
                targetValues.protein().subtract(consumed.protein()),
                targetValues.carbs().subtract(consumed.carbs()),
                targetValues.fat().subtract(consumed.fat())
        );

        return new DailyDashboardResponse(userId, date, targetValues, consumed, remaining, logs);
    }

    private FoodLogResponse toResponse(FoodLog log) {
        NutritionValuesResponse nutrition = foodNutritionCalculator.calculate(
                log.getCaloriesPer100gSnapshot(),
                log.getProteinPer100gSnapshot(),
                log.getCarbsPer100gSnapshot(),
                log.getFatPer100gSnapshot(),
                log.getGrams()
        );

        return new FoodLogResponse(
                log.getId(), log.getFood().getId(), log.getFoodNameSnapshot(), log.getGrams(),
                log.getMealType(), log.getEatenAt(), nutrition
        );
    }

    private NutritionValuesResponse sumNutrition(List<FoodLogResponse> logs) {
        BigDecimal calories = BigDecimal.ZERO;
        BigDecimal protein = BigDecimal.ZERO;
        BigDecimal carbs = BigDecimal.ZERO;
        BigDecimal fat = BigDecimal.ZERO;

        for (FoodLogResponse log : logs) {
            calories = calories.add(log.nutrition().calories());
            protein = protein.add(log.nutrition().protein());
            carbs = carbs.add(log.nutrition().carbs());
            fat = fat.add(log.nutrition().fat());
        }
        return values(calories, protein, carbs, fat);
    }

    private NutritionValuesResponse values(BigDecimal calories, BigDecimal protein,
                                             BigDecimal carbs, BigDecimal fat) {
        return new NutritionValuesResponse(
                calories.setScale(DISPLAY_SCALE, java.math.RoundingMode.HALF_UP),
                protein.setScale(DISPLAY_SCALE, java.math.RoundingMode.HALF_UP),
                carbs.setScale(DISPLAY_SCALE, java.math.RoundingMode.HALF_UP),
                fat.setScale(DISPLAY_SCALE, java.math.RoundingMode.HALF_UP)
        );
    }
}
