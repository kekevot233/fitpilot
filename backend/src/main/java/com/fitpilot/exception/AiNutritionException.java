package com.fitpilot.exception;

public class AiNutritionException extends RuntimeException {

    private final boolean unavailable;

    public AiNutritionException(String message, boolean unavailable) {
        super(message);
        this.unavailable = unavailable;
    }

    public AiNutritionException(String message, boolean unavailable, Throwable cause) {
        super(message, cause);
        this.unavailable = unavailable;
    }

    public boolean isUnavailable() {
        return unavailable;
    }
}
