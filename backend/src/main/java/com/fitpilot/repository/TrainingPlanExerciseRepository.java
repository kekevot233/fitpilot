package com.fitpilot.repository;

import com.fitpilot.domain.TrainingPlanExercise;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TrainingPlanExerciseRepository extends JpaRepository<TrainingPlanExercise, Long> {

    List<TrainingPlanExercise> findByTrainingPlanIdOrderByDayNumberAscPositionAsc(Long trainingPlanId);
}
