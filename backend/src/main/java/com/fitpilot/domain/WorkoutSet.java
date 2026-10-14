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

import java.math.BigDecimal;

@Entity
@Table(
        name = "workout_sets",
        indexes = @Index(name = "idx_workout_sets_workout", columnList = "workout_id")
)
public class WorkoutSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workout_id", nullable = false)
    private Workout workout;

    @Column(nullable = false, length = 100)
    private String exerciseName;

    @Column(nullable = false)
    private Integer setNumber;

    @Column(nullable = false, precision = 7, scale = 2)
    private BigDecimal weightKg;

    @Column(nullable = false)
    private Integer reps;

    @Column(nullable = false)
    private Integer rir;

    protected WorkoutSet() {
        // Required by JPA.
    }

    public WorkoutSet(Workout workout, String exerciseName, Integer setNumber,
                      BigDecimal weightKg, Integer reps, Integer rir) {
        this.workout = workout;
        this.exerciseName = exerciseName;
        this.setNumber = setNumber;
        this.weightKg = weightKg;
        this.reps = reps;
        this.rir = rir;
    }

    public Long getId() {
        return id;
    }

    public String getExerciseName() {
        return exerciseName;
    }

    public Integer getSetNumber() {
        return setNumber;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public Integer getReps() {
        return reps;
    }

    public Integer getRir() {
        return rir;
    }
}
