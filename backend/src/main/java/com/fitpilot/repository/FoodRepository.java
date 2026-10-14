package com.fitpilot.repository;

import com.fitpilot.domain.Food;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FoodRepository extends JpaRepository<Food, Long> {

    boolean existsByOwnerIsNullAndNameIgnoreCase(String name);

    Optional<Food> findByOwnerIsNullAndNameIgnoreCase(String name);

    @Query("""
            select food from Food food
            where (food.owner is null or food.owner.id = :userId)
              and (food.active is null or food.active = true)
              and (:keyword = '' or lower(food.name) like lower(concat('%', :keyword, '%')))
            order by food.name asc
            """)
    List<Food> findVisibleToUser(@Param("userId") Long userId, @Param("keyword") String keyword);

    @Query("""
            select food from Food food
            where food.id = :foodId
              and (food.owner is null or food.owner.id = :userId)
              and (food.active is null or food.active = true)
            """)
    Optional<Food> findAccessibleById(@Param("foodId") Long foodId, @Param("userId") Long userId);

    @Query("""
            select food from Food food
            where food.owner.id = :userId
              and (food.active is null or food.active = true)
            order by food.name asc
            """)
    List<Food> findActiveCustomFoods(@Param("userId") Long userId);

    @Query("""
            select food from Food food
            where food.id = :foodId
              and food.owner.id = :userId
              and (food.active is null or food.active = true)
            """)
    Optional<Food> findActiveCustomById(
            @Param("foodId") Long foodId,
            @Param("userId") Long userId
    );

    @Query("""
            select (count(food) > 0) from Food food
            where food.owner.id = :userId
              and lower(food.name) = lower(:name)
              and food.id <> :excludedId
              and (food.active is null or food.active = true)
            """)
    boolean existsActiveCustomName(
            @Param("userId") Long userId,
            @Param("name") String name,
            @Param("excludedId") Long excludedId
    );
}
