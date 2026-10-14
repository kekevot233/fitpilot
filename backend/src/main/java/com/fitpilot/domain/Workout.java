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

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "workouts",
        indexes = @Index(name = "idx_workouts_user_date", columnList = "user_id,workout_date")
)
public class Workout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "training_plan_id")
    private TrainingPlan trainingPlan;

    @Column(nullable = false)
    private LocalDate workoutDate;

    @Column(nullable = false, length = 50)
    private String workoutType;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected Workout() {
        // Required by JPA.
    }

    public Workout(User user, TrainingPlan trainingPlan, LocalDate workoutDate,
                   String workoutType, LocalDateTime createdAt) {
        this.user = user;
        this.trainingPlan = trainingPlan;
        this.workoutDate = workoutDate;
        this.workoutType = workoutType;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public TrainingPlan getTrainingPlan() {
        return trainingPlan;
    }

    public LocalDate getWorkoutDate() {
        return workoutDate;
    }

    public String getWorkoutType() {
        return workoutType;
    }
}
