package com.fitpilot.service;

import com.fitpilot.domain.TrainingPlan;
import com.fitpilot.domain.TrainingPlanExercise;
import com.fitpilot.domain.User;
import com.fitpilot.dto.training.TrainingDayResponse;
import com.fitpilot.dto.training.TrainingPlanExerciseResponse;
import com.fitpilot.dto.training.TrainingPlanResponse;
import com.fitpilot.exception.ResourceNotFoundException;
import com.fitpilot.repository.TrainingPlanExerciseRepository;
import com.fitpilot.repository.TrainingPlanRepository;
import com.fitpilot.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Profile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Profile("!no-db")
public class TrainingPlanService {

    private final UserRepository userRepository;
    private final TrainingPlanRepository trainingPlanRepository;
    private final TrainingPlanExerciseRepository exerciseRepository;

    public TrainingPlanService(UserRepository userRepository,
                               TrainingPlanRepository trainingPlanRepository,
                               TrainingPlanExerciseRepository exerciseRepository) {
        this.userRepository = userRepository;
        this.trainingPlanRepository = trainingPlanRepository;
        this.exerciseRepository = exerciseRepository;
    }

    @Transactional
    public TrainingPlanResponse generate(long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User with id " + userId + " was not found."));

        trainingPlanRepository.findByUserIdAndActiveTrue(userId)
                .forEach(TrainingPlan::deactivate);

        int frequency = user.getWeeklyTrainingFrequency();
        TrainingPlan plan = trainingPlanRepository.save(new TrainingPlan(
                user, planName(frequency), frequency, LocalDateTime.now()
        ));

        List<DayTemplate> templates = templatesFor(frequency);
        List<TrainingPlanExercise> exercises = new ArrayList<>();
        for (int dayIndex = 0; dayIndex < frequency; dayIndex++) {
            DayTemplate day = templates.get(dayIndex % templates.size());
            for (int exerciseIndex = 0; exerciseIndex < day.exercises().size(); exerciseIndex++) {
                ExerciseTemplate exercise = day.exercises().get(exerciseIndex);
                exercises.add(new TrainingPlanExercise(
                        plan, dayIndex + 1, day.name(), exercise.name(), exercise.sets(),
                        exercise.minReps(), exercise.maxReps(), 2, exerciseIndex + 1
                ));
            }
        }
        List<TrainingPlanExercise> savedExercises = exerciseRepository.saveAll(exercises);
        return toResponse(plan, savedExercises);
    }

    @Transactional(readOnly = true)
    public TrainingPlanResponse getCurrent(long userId) {
        TrainingPlan plan = trainingPlanRepository
                .findFirstByUserIdAndActiveTrueOrderByCreatedAtDescIdDesc(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No active training plan exists for user with id " + userId + "."
                ));
        List<TrainingPlanExercise> exercises =
                exerciseRepository.findByTrainingPlanIdOrderByDayNumberAscPositionAsc(plan.getId());
        return toResponse(plan, exercises);
    }

    private TrainingPlanResponse toResponse(TrainingPlan plan, List<TrainingPlanExercise> exercises) {
        Map<Integer, List<TrainingPlanExercise>> grouped = new LinkedHashMap<>();
        exercises.forEach(exercise -> grouped
                .computeIfAbsent(exercise.getDayNumber(), ignored -> new ArrayList<>())
                .add(exercise));

        List<TrainingDayResponse> days = grouped.values().stream()
                .map(dayExercises -> new TrainingDayResponse(
                        dayExercises.getFirst().getDayNumber(),
                        dayExercises.getFirst().getDayName(),
                        dayExercises.stream()
                                .map(exercise -> new TrainingPlanExerciseResponse(
                                        exercise.getId(), exercise.getExerciseName(), exercise.getTargetSets(),
                                        exercise.getMinReps(), exercise.getMaxReps(), exercise.getTargetRir()
                                ))
                                .toList()
                ))
                .toList();

        return new TrainingPlanResponse(
                plan.getId(), plan.getName(), plan.getWeeklyFrequency(),
                plan.isActive(), plan.getCreatedAt(), days
        );
    }

    private String planName(int frequency) {
        if (frequency <= 3) {
            return frequency + "-Day Full Body Beginner Plan";
        }
        return frequency + "-Day Upper/Lower Beginner Plan";
    }

    private List<DayTemplate> templatesFor(int frequency) {
        return frequency <= 3 ? fullBodyTemplates() : upperLowerTemplates();
    }

    private List<DayTemplate> fullBodyTemplates() {
        return List.of(
                new DayTemplate("全身 A", List.of(
                        exercise("深蹲", 3, 8, 12),
                        exercise("杠铃卧推", 3, 8, 12),
                        exercise("高位下拉", 3, 8, 12),
                        exercise("罗马尼亚硬拉", 2, 8, 12)
                )),
                new DayTemplate("全身 B", List.of(
                        exercise("硬拉", 3, 5, 8),
                        exercise("哑铃肩推", 3, 8, 12),
                        exercise("坐姿划船", 3, 8, 12),
                        exercise("保加利亚分腿蹲", 2, 8, 12)
                )),
                new DayTemplate("全身 C", List.of(
                        exercise("腿举", 3, 10, 15),
                        exercise("上斜哑铃卧推", 3, 8, 12),
                        exercise("辅助引体向上", 3, 6, 10),
                        exercise("臀推", 3, 8, 12)
                ))
        );
    }

    private List<DayTemplate> upperLowerTemplates() {
        return List.of(
                new DayTemplate("上肢 A", List.of(
                        exercise("杠铃卧推", 3, 8, 12),
                        exercise("坐姿划船", 3, 8, 12),
                        exercise("哑铃肩推", 3, 8, 12),
                        exercise("高位下拉", 3, 8, 12)
                )),
                new DayTemplate("下肢 A", List.of(
                        exercise("深蹲", 3, 8, 12),
                        exercise("罗马尼亚硬拉", 3, 8, 12),
                        exercise("腿举", 3, 10, 15),
                        exercise("站姿提踵", 3, 10, 15)
                )),
                new DayTemplate("上肢 B", List.of(
                        exercise("上斜哑铃卧推", 3, 8, 12),
                        exercise("高位下拉", 3, 8, 12),
                        exercise("哑铃侧平举", 3, 10, 15),
                        exercise("坐姿划船", 3, 8, 12)
                )),
                new DayTemplate("下肢 B", List.of(
                        exercise("硬拉", 3, 5, 8),
                        exercise("保加利亚分腿蹲", 3, 8, 12),
                        exercise("腿弯举", 3, 10, 15),
                        exercise("臀推", 3, 8, 12)
                ))
        );
    }

    private ExerciseTemplate exercise(String name, int sets, int minReps, int maxReps) {
        return new ExerciseTemplate(name, sets, minReps, maxReps);
    }

    private record DayTemplate(String name, List<ExerciseTemplate> exercises) {
    }

    private record ExerciseTemplate(String name, int sets, int minReps, int maxReps) {
    }
}
