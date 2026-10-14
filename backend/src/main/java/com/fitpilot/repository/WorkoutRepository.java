package com.fitpilot.repository;

import com.fitpilot.domain.Workout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkoutRepository extends JpaRepository<Workout, Long> {

    List<Workout> findByUserIdOrderByWorkoutDateDescIdDesc(Long userId);
}
