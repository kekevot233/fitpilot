package com.fitpilot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, length = 254)
    private String email;

    @Column(length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 40)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Gender gender;

    @Column(nullable = false)
    private Integer age;

    @Column(nullable = false)
    private Double heightCm;

    @Column(nullable = false)
    private Double weightKg;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ActivityLevel activityLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Goal goal;

    @Column(nullable = false)
    private Integer weeklyTrainingFrequency;

    protected User() {
        // Required by JPA.
    }

    public User(String nickname, Gender gender, Integer age, Double heightCm, Double weightKg,
                ActivityLevel activityLevel, Goal goal, Integer weeklyTrainingFrequency) {
        this.nickname = nickname;
        this.gender = gender;
        this.age = age;
        this.heightCm = heightCm;
        this.weightKg = weightKg;
        this.activityLevel = activityLevel;
        this.goal = goal;
        this.weeklyTrainingFrequency = weeklyTrainingFrequency;
    }

    public User(String email, String passwordHash, String nickname, Gender gender, Integer age,
                Double heightCm, Double weightKg, ActivityLevel activityLevel, Goal goal,
                Integer weeklyTrainingFrequency) {
        this(nickname, gender, age, heightCm, weightKg, activityLevel, goal, weeklyTrainingFrequency);
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public Long getId() {
        return id;
    }

    public String getNickname() {
        return nickname;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Gender getGender() {
        return gender;
    }

    public Integer getAge() {
        return age;
    }

    public Double getHeightCm() {
        return heightCm;
    }

    public Double getWeightKg() {
        return weightKg;
    }

    public ActivityLevel getActivityLevel() {
        return activityLevel;
    }

    public Goal getGoal() {
        return goal;
    }

    public void updateGoal(Goal goal) {
        this.goal = goal;
    }

    public void updateBodyProfile(Integer age, Double heightCm, Double weightKg,
                                  ActivityLevel activityLevel, Integer weeklyTrainingFrequency) {
        this.age = age;
        this.heightCm = heightCm;
        this.weightKg = weightKg;
        this.activityLevel = activityLevel;
        this.weeklyTrainingFrequency = weeklyTrainingFrequency;
    }

    public Integer getWeeklyTrainingFrequency() {
        return weeklyTrainingFrequency;
    }
}
