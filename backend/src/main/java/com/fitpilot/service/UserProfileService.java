package com.fitpilot.service;

import com.fitpilot.domain.NutritionTarget;
import com.fitpilot.domain.User;
import com.fitpilot.domain.Goal;
import com.fitpilot.dto.nutrition.NutritionCalculationRequest;
import com.fitpilot.dto.nutrition.NutritionCalculationResponse;
import com.fitpilot.dto.auth.RegisterRequest;
import com.fitpilot.dto.user.NutritionTargetResponse;
import com.fitpilot.dto.user.UpdateUserProfileRequest;
import com.fitpilot.dto.user.UserProfileResponse;
import com.fitpilot.exception.ResourceNotFoundException;
import com.fitpilot.repository.NutritionTargetRepository;
import com.fitpilot.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Profile;

import java.time.LocalDate;

@Service
@Profile("!no-db")
public class UserProfileService {

    // Retained for the legacy training API; nutrition calculations use activityLevel only.
    private static final int DEFAULT_TRAINING_FREQUENCY = 3;

    private final UserRepository userRepository;
    private final NutritionTargetRepository nutritionTargetRepository;
    private final NutritionCalculationService nutritionCalculationService;

    public UserProfileService(UserRepository userRepository,
                              NutritionTargetRepository nutritionTargetRepository,
                              NutritionCalculationService nutritionCalculationService) {
        this.userRepository = userRepository;
        this.nutritionTargetRepository = nutritionTargetRepository;
        this.nutritionCalculationService = nutritionCalculationService;
    }

    @Transactional
    public UserProfileResponse createRegisteredProfile(RegisterRequest request, String passwordHash) {
        User user = userRepository.save(new User(
                request.email().trim().toLowerCase(), passwordHash,
                request.nickname().trim(), request.gender(), request.age(), request.heightCm(), request.weightKg(),
                request.activityLevel(), request.goal(),
                request.weeklyTrainingFrequency() == null ? DEFAULT_TRAINING_FREQUENCY : request.weeklyTrainingFrequency()
        ));

        NutritionCalculationResponse calculation = nutritionCalculationService.calculate(
                new NutritionCalculationRequest(
                        request.gender(), request.age(), request.heightCm(), request.weightKg(),
                        request.activityLevel(), request.goal()
                )
        );

        NutritionTarget target = nutritionTargetRepository.save(new NutritionTarget(
                user,
                calculation.bmr(), calculation.tdee(), calculation.targetCalories(),
                calculation.protein(), calculation.carbs(), calculation.fat(), LocalDate.now()
        ));

        return toResponse(user, target);
    }

    @Transactional
    public UserProfileResponse updateNutritionPlan(long userId, Goal goal) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User with id " + userId + " was not found."));

        user.updateGoal(goal);
        // Append a dated target so earlier dashboards keep their original targets.
        NutritionTarget target = calculateAndSaveTarget(user);
        return toResponse(user, target);
    }

    @Transactional
    public UserProfileResponse updateProfile(long userId, UpdateUserProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User with id " + userId + " was not found."));

        user.updateBodyProfile(
                request.age(), request.heightCm(), request.weightKg(),
                request.activityLevel(),
                request.weeklyTrainingFrequency() == null ? user.getWeeklyTrainingFrequency() : request.weeklyTrainingFrequency()
        );

        // The user is managed by JPA, so the profile fields are persisted by dirty checking at commit.
        NutritionTarget target = calculateAndSaveTarget(user);
        return toResponse(user, target);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User with id " + userId + " was not found."));

        NutritionTarget target = nutritionTargetRepository
                .findTopByUserIdOrderByEffectiveDateDescIdDesc(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No nutrition target exists for user with id " + userId + "."
                ));

        return toResponse(user, target);
    }

    private NutritionTarget calculateAndSaveTarget(User user) {
        NutritionCalculationResponse calculation = nutritionCalculationService.calculate(
                new NutritionCalculationRequest(
                        user.getGender(), user.getAge(), user.getHeightCm(), user.getWeightKg(),
                        user.getActivityLevel(), user.getGoal()
                )
        );

        return nutritionTargetRepository.save(new NutritionTarget(
                user,
                calculation.bmr(), calculation.tdee(), calculation.targetCalories(),
                calculation.protein(), calculation.carbs(), calculation.fat(), LocalDate.now()
        ));
    }

    private UserProfileResponse toResponse(User user, NutritionTarget target) {
        return new UserProfileResponse(
                user.getId(), user.getEmail(), user.getNickname(), user.getGender(), user.getAge(), user.getHeightCm(),
                user.getWeightKg(), user.getActivityLevel(), user.getGoal(), user.getWeeklyTrainingFrequency(),
                new NutritionTargetResponse(
                        target.getBmr(), target.getTdee(), target.getCalories(), target.getProteinG(),
                        target.getCarbsG(), target.getFatG(), target.getEffectiveDate()
                )
        );
    }
}
