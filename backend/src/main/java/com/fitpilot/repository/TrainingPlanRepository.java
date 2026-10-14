package com.fitpilot.repository;

import com.fitpilot.domain.TrainingPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TrainingPlanRepository extends JpaRepository<TrainingPlan, Long> {

    List<TrainingPlan> findByUserIdAndActiveTrue(Long userId);

    Optional<TrainingPlan> findFirstByUserIdAndActiveTrueOrderByCreatedAtDescIdDesc(Long userId);

    Optional<TrainingPlan> findByIdAndUserId(Long id, Long userId);
}
