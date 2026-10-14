package com.fitpilot.service;

import com.fitpilot.domain.TrainingPlan;
import com.fitpilot.domain.User;
import com.fitpilot.domain.Workout;
import com.fitpilot.domain.WorkoutSet;
import com.fitpilot.dto.training.CreateWorkoutRequest;
import com.fitpilot.dto.training.WorkoutResponse;
import com.fitpilot.dto.training.WorkoutSetResponse;
import com.fitpilot.exception.ResourceNotFoundException;
import com.fitpilot.repository.TrainingPlanRepository;
import com.fitpilot.repository.UserRepository;
import com.fitpilot.repository.WorkoutRepository;
import com.fitpilot.repository.WorkoutSetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Profile;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Profile("!no-db")
public class WorkoutService {

    private final UserRepository userRepository;
    private final TrainingPlanRepository trainingPlanRepository;
    private final WorkoutRepository workoutRepository;
    private final WorkoutSetRepository workoutSetRepository;

    public WorkoutService(UserRepository userRepository,
                          TrainingPlanRepository trainingPlanRepository,
                          WorkoutRepository workoutRepository,
                          WorkoutSetRepository workoutSetRepository) {
        this.userRepository = userRepository;
        this.trainingPlanRepository = trainingPlanRepository;
        this.workoutRepository = workoutRepository;
        this.workoutSetRepository = workoutSetRepository;
    }

    @Transactional
    public WorkoutResponse create(long userId, CreateWorkoutRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User with id " + userId + " was not found."));

        TrainingPlan plan = null;
        if (request.trainingPlanId() != null) {
            plan = trainingPlanRepository.findByIdAndUserId(request.trainingPlanId(), userId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Training plan with id " + request.trainingPlanId()
                                    + " was not found for user with id " + userId + "."
                    ));
        }

        Workout workout = workoutRepository.save(new Workout(
                user, plan, request.workoutDate(), request.workoutType().trim(), LocalDateTime.now()
        ));
        List<WorkoutSet> sets = request.sets().stream()
                .map(set -> new WorkoutSet(
                        workout, set.exerciseName().trim(), set.setNumber(),
                        set.weightKg(), set.reps(), set.rir()
                ))
                .toList();
        List<WorkoutSet> savedSets = workoutSetRepository.saveAll(sets);
        return toResponse(workout, savedSets);
    }

    @Transactional(readOnly = true)
    public List<WorkoutResponse> findAll(long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User with id " + userId + " was not found.");
        }
        return workoutRepository.findByUserIdOrderByWorkoutDateDescIdDesc(userId).stream()
                .map(workout -> toResponse(
                        workout,
                        workoutSetRepository.findByWorkoutIdOrderByExerciseNameAscSetNumberAsc(workout.getId())
                ))
                .toList();
    }

    private WorkoutResponse toResponse(Workout workout, List<WorkoutSet> sets) {
        Long trainingPlanId = workout.getTrainingPlan() == null ? null : workout.getTrainingPlan().getId();
        return new WorkoutResponse(
                workout.getId(), trainingPlanId, workout.getWorkoutDate(), workout.getWorkoutType(),
                sets.stream()
                        .map(set -> new WorkoutSetResponse(
                                set.getId(), set.getExerciseName(), set.getSetNumber(),
                                set.getWeightKg(), set.getReps(), set.getRir()
                        ))
                        .toList()
        );
    }
}
