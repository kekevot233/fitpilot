package com.fitpilot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "foods")
public class Food {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Column
    private Boolean active = true;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal caloriesPer100g;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal proteinPer100g;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal carbsPer100g;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal fatPer100g;

    protected Food() {
        // Required by JPA.
    }

    public Food(String name, BigDecimal caloriesPer100g, BigDecimal proteinPer100g,
                BigDecimal carbsPer100g, BigDecimal fatPer100g) {
        this(null, name, caloriesPer100g, proteinPer100g, carbsPer100g, fatPer100g);
    }

    public Food(User owner, String name, BigDecimal caloriesPer100g, BigDecimal proteinPer100g,
                BigDecimal carbsPer100g, BigDecimal fatPer100g) {
        this.owner = owner;
        this.name = name;
        this.caloriesPer100g = caloriesPer100g;
        this.proteinPer100g = proteinPer100g;
        this.carbsPer100g = carbsPer100g;
        this.fatPer100g = fatPer100g;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public User getOwner() {
        return owner;
    }

    public boolean isActive() {
        return active == null || active;
    }

    public BigDecimal getCaloriesPer100g() {
        return caloriesPer100g;
    }

    public BigDecimal getProteinPer100g() {
        return proteinPer100g;
    }

    public BigDecimal getCarbsPer100g() {
        return carbsPer100g;
    }

    public BigDecimal getFatPer100g() {
        return fatPer100g;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void updateNutrition(BigDecimal caloriesPer100g, BigDecimal proteinPer100g,
                                BigDecimal carbsPer100g, BigDecimal fatPer100g) {
        this.caloriesPer100g = caloriesPer100g;
        this.proteinPer100g = proteinPer100g;
        this.carbsPer100g = carbsPer100g;
        this.fatPer100g = fatPer100g;
    }

    public void updateDetails(String name, BigDecimal caloriesPer100g, BigDecimal proteinPer100g,
                              BigDecimal carbsPer100g, BigDecimal fatPer100g) {
        rename(name);
        updateNutrition(caloriesPer100g, proteinPer100g, carbsPer100g, fatPer100g);
    }

    public void archive() {
        this.active = false;
    }
}
