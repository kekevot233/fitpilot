package com.fitpilot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "food_logs",
        indexes = @Index(name = "idx_food_logs_user_eaten_at", columnList = "user_id,eaten_at")
)
public class FoodLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "food_id", nullable = false)
    private Food food;

    @Column(name = "food_name_snapshot", nullable = false, length = 100)
    private String foodNameSnapshot;

    @Column(name = "calories_per_100g_snapshot", nullable = false, precision = 8, scale = 2)
    private BigDecimal caloriesPer100gSnapshot;

    @Column(name = "protein_per_100g_snapshot", nullable = false, precision = 6, scale = 2)
    private BigDecimal proteinPer100gSnapshot;

    @Column(name = "carbs_per_100g_snapshot", nullable = false, precision = 6, scale = 2)
    private BigDecimal carbsPer100gSnapshot;

    @Column(name = "fat_per_100g_snapshot", nullable = false, precision = 6, scale = 2)
    private BigDecimal fatPer100gSnapshot;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal grams;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MealType mealType;

    @Column(nullable = false)
    private LocalDateTime eatenAt;

    protected FoodLog() {
        // Required by JPA.
    }

    public FoodLog(User user, Food food, BigDecimal grams, MealType mealType, LocalDateTime eatenAt) {
        this.user = user;
        changeFood(food);
        this.grams = grams;
        this.mealType = mealType;
        this.eatenAt = eatenAt;
    }

    private FoodLog(User user, Food food,
                    String foodNameSnapshot,
                    BigDecimal caloriesPer100gSnapshot,
                    BigDecimal proteinPer100gSnapshot,
                    BigDecimal carbsPer100gSnapshot,
                    BigDecimal fatPer100gSnapshot,
                    BigDecimal grams, MealType mealType, LocalDateTime eatenAt) {
        this.user = user;
        this.food = food;
        this.foodNameSnapshot = foodNameSnapshot;
        this.caloriesPer100gSnapshot = caloriesPer100gSnapshot;
        this.proteinPer100gSnapshot = proteinPer100gSnapshot;
        this.carbsPer100gSnapshot = carbsPer100gSnapshot;
        this.fatPer100gSnapshot = fatPer100gSnapshot;
        this.grams = grams;
        this.mealType = mealType;
        this.eatenAt = eatenAt;
    }

    public Long getId() {
        return id;
    }

    public Food getFood() {
        return food;
    }

    public String getFoodNameSnapshot() {
        return foodNameSnapshot;
    }

    public BigDecimal getCaloriesPer100gSnapshot() {
        return caloriesPer100gSnapshot;
    }

    public BigDecimal getProteinPer100gSnapshot() {
        return proteinPer100gSnapshot;
    }

    public BigDecimal getCarbsPer100gSnapshot() {
        return carbsPer100gSnapshot;
    }

    public BigDecimal getFatPer100gSnapshot() {
        return fatPer100gSnapshot;
    }

    public BigDecimal getGrams() {
        return grams;
    }

    public MealType getMealType() {
        return mealType;
    }

    public LocalDateTime getEatenAt() {
        return eatenAt;
    }

    public void update(Food food, BigDecimal grams, MealType mealType, LocalDateTime eatenAt) {
        boolean sameFood = this.food == food
                || (this.food.getId() != null && Objects.equals(this.food.getId(), food.getId()));
        if (!sameFood) {
            changeFood(food);
        } else {
            this.food = food;
        }
        this.grams = grams;
        this.mealType = mealType;
        this.eatenAt = eatenAt;
    }

    public FoodLog copyTo(User user, LocalDateTime eatenAt) {
        return new FoodLog(
                user, food,
                foodNameSnapshot,
                caloriesPer100gSnapshot,
                proteinPer100gSnapshot,
                carbsPer100gSnapshot,
                fatPer100gSnapshot,
                grams, mealType, eatenAt
        );
    }

    private void changeFood(Food food) {
        this.food = food;
        this.foodNameSnapshot = food.getName();
        this.caloriesPer100gSnapshot = food.getCaloriesPer100g();
        this.proteinPer100gSnapshot = food.getProteinPer100g();
        this.carbsPer100gSnapshot = food.getCarbsPer100g();
        this.fatPer100gSnapshot = food.getFatPer100g();
    }
}
