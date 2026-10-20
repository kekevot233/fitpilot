package com.fitpilot.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties("fitpilot.cache.food-search")
public class FoodCacheProperties {
    private boolean enabled = true;
    @Min(1) @Max(3600)
    private int ttlSeconds = 300;
    @Min(1) @Max(60)
    private int failureBackoffSeconds = 5;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getTtlSeconds() { return ttlSeconds; }
    public void setTtlSeconds(int ttlSeconds) { this.ttlSeconds = ttlSeconds; }
    public int getFailureBackoffSeconds() { return failureBackoffSeconds; }
    public void setFailureBackoffSeconds(int failureBackoffSeconds) { this.failureBackoffSeconds = failureBackoffSeconds; }
}
