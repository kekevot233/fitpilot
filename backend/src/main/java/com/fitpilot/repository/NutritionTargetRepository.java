package com.fitpilot.repository;

import com.fitpilot.domain.NutritionTarget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface NutritionTargetRepository extends JpaRepository<NutritionTarget, Long> {

    Optional<NutritionTarget> findTopByUserIdOrderByEffectiveDateDescIdDesc(Long userId);

    Optional<NutritionTarget> findTopByUserIdAndEffectiveDateLessThanEqualOrderByEffectiveDateDescIdDesc(
            Long userId,
            LocalDate date
    );

    List<NutritionTarget> findByUserIdAndEffectiveDateLessThanEqualOrderByEffectiveDateAscIdAsc(
            Long userId,
            LocalDate endDate
    );
}
