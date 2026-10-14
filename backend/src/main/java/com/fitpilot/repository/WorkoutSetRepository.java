package com.fitpilot.repository;

import com.fitpilot.domain.WorkoutSet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkoutSetRepository extends JpaRepository<WorkoutSet, Long> {

    List<WorkoutSet> findByWorkoutIdOrderByExerciseNameAscSetNumberAsc(Long workoutId);
}
