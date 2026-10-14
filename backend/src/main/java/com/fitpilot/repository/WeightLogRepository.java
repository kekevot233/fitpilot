package com.fitpilot.repository;

import com.fitpilot.domain.WeightLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WeightLogRepository extends JpaRepository<WeightLog, Long> {

    Optional<WeightLog> findByUserIdAndRecordedDate(Long userId, LocalDate recordedDate);

    List<WeightLog> findByUserIdAndRecordedDateBetweenOrderByRecordedDateAsc(
            Long userId,
            LocalDate startDate,
            LocalDate endDate
    );
}
