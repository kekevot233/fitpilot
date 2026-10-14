package com.fitpilot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(
        name = "training_plan_exercises",
        indexes = @Index(name = "idx_plan_exercises_plan_day", columnList = "training_plan_id,day_number")
)
public class TrainingPlanExercise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "training_plan_id", nullable = false)
    private TrainingPlan trainingPlan;

    @Column(nullable = false)
    private Integer dayNumber;

    @Column(nullable = false, length = 50)
    private String dayName;

    @Column(nullable = false, length = 100)
    private String exerciseName;

    @Column(nullable = false)
    private Integer targetSets;

    @Column(nullable = false)
    private Integer minReps;

    @Column(nullable = false)
    private Integer maxReps;

    @Column(nullable = false)
    private Integer targetRir;

    @Column(nullable = false)
    private Integer position;

    protected TrainingPlanExercise() {
        // Required by JPA.
    }

    public TrainingPlanExercise(TrainingPlan trainingPlan, Integer dayNumber, String dayName,
                                String exerciseName, Integer targetSets, Integer minReps,
                                Integer maxReps, Integer targetRir, Integer position) {
        this.trainingPlan = trainingPlan;
        this.dayNumber = dayNumber;
        this.dayName = dayName;
        this.exerciseName = exerciseName;
        this.targetSets = targetSets;
        this.minReps = minReps;
        this.maxReps = maxReps;
        this.targetRir = targetRir;
        this.position = position;
    }

    public Long getId() {
        return id;
    }

    public Integer getDayNumber() {
        return dayNumber;
    }

    public String getDayName() {
        return dayName;
    }

    public String getExerciseName() {
        return exerciseName;
    }

    public Integer getTargetSets() {
        return targetSets;
    }

    public Integer getMinReps() {
        return minReps;
    }

    public Integer getMaxReps() {
        return maxReps;
    }

    public Integer getTargetRir() {
        return targetRir;
    }
}
