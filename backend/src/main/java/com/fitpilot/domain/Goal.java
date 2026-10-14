package com.fitpilot.domain;

public enum Goal {
    FAT_LOSS(-400),
    MAINTAIN(0),
    LEAN_BULK(250);

    private final int calorieAdjustment;

    Goal(int calorieAdjustment) {
        this.calorieAdjustment = calorieAdjustment;
    }

    public int getCalorieAdjustment() {
        return calorieAdjustment;
    }
}
