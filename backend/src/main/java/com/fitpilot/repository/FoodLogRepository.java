package com.fitpilot.repository;

import com.fitpilot.domain.FoodLog;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FoodLogRepository extends JpaRepository<FoodLog, Long> {

    @EntityGraph(attributePaths = "food")
    Optional<FoodLog> findByIdAndUserId(Long id, Long userId);

    @EntityGraph(attributePaths = "food")
    List<FoodLog> findByUserIdAndEatenAtGreaterThanEqualAndEatenAtLessThanOrderByEatenAtAsc(
            Long userId,
            LocalDateTime start,
            LocalDateTime end
    );

    boolean existsByUserIdAndEatenAtGreaterThanEqualAndEatenAtLessThan(
            Long userId,
            LocalDateTime start,
            LocalDateTime end
    );

    @EntityGraph(attributePaths = "food")
    @Query("""
            select log
            from FoodLog log
            where log.user.id = :userId
              and (log.food.active is null or log.food.active = true)
              and not exists (
                  select newer.id
                  from FoodLog newer
                  where newer.user.id = :userId
                    and newer.food = log.food
                    and (
                        newer.eatenAt > log.eatenAt
                        or (newer.eatenAt = log.eatenAt and newer.id > log.id)
                    )
              )
            order by log.eatenAt desc, log.id desc
            """)
    List<FoodLog> findRecentlyLoggedFoods(
            @Param("userId") Long userId,
            Pageable pageable
    );
}
