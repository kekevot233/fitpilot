package com.fitpilot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "nutrition_targets")
public class NutritionTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Integer bmr;

    @Column(nullable = false)
    private Integer tdee;

    @Column(nullable = false)
    private Integer calories;

    @Column(nullable = false)
    private Integer proteinG;

    @Column(nullable = false)
    private Integer carbsG;

    @Column(nullable = false)
    private Integer fatG;

    @Column(nullable = false)
    private LocalDate effectiveDate;

    protected NutritionTarget() {
        // Required by JPA.
    }

    public NutritionTarget(User user, int bmr, int tdee, int calories, int proteinG,
                           int carbsG, int fatG, LocalDate effectiveDate) {
        this.user = user;
        this.bmr = bmr;
        this.tdee = tdee;
        this.calories = calories;
        this.proteinG = proteinG;
        this.carbsG = carbsG;
        this.fatG = fatG;
        this.effectiveDate = effectiveDate;
    }

    public Long getId() {
        return id;
    }

    public Integer getBmr() {
        return bmr;
    }

    public Integer getTdee() {
        return tdee;
    }

    public Integer getCalories() {
        return calories;
    }

    public Integer getProteinG() {
        return proteinG;
    }

    public Integer getCarbsG() {
        return carbsG;
    }

    public Integer getFatG() {
        return fatG;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }
}
